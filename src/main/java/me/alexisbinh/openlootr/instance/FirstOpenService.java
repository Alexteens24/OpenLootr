package me.alexisbinh.openlootr.instance;

import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.LootStorage;
import me.alexisbinh.openlootr.storage.StorageException;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Establishes one canonical first-open row before callers may expose its contents.
 * The generator dispatcher must move generation onto the owning Paper scheduler; it is invoked
 * after an asynchronous DB miss and must never touch live Bukkit objects directly on that callback.
 */
public final class FirstOpenService {
    private final LootStorage storage;
    private final DbExecutor dbExecutor;
    private final InFlightRegistry<InstanceKey, LootInstanceRecord> inFlight = new InFlightRegistry<>();

    public FirstOpenService(LootStorage storage, DbExecutor dbExecutor) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    public CompletableFuture<LootInstanceRecord> establish(
            InstanceKey key,
            Function<InstanceKey, CompletableFuture<LootInstanceRecord>> generationDispatcher
    ) {
        Objects.requireNonNull(generationDispatcher, "generationDispatcher");
        return inFlight.runOrJoin(key, () -> dbExecutor.supply(() -> storage.find(key))
                .thenCompose(found -> found
                        .map(CompletableFuture::completedFuture)
                        .orElseGet(() -> dispatchGeneration(key, generationDispatcher)))
        );
    }

    private CompletableFuture<LootInstanceRecord> dispatchGeneration(
            InstanceKey key,
            Function<InstanceKey, CompletableFuture<LootInstanceRecord>> generationDispatcher
    ) {
        final CompletableFuture<LootInstanceRecord> generation;
        try {
            generation = Objects.requireNonNull(generationDispatcher.apply(key),
                    "generationDispatcher returned null");
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
        return generation.thenCompose(record -> {
            if (!record.key().equals(key)) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("generator returned a record for a different key"));
            }
            return persistOrRecover(record);
        });
    }

    private CompletableFuture<LootInstanceRecord> persistOrRecover(LootInstanceRecord generated) {
        return dbExecutor.supply(() -> {
            try {
                if (storage.insertFirst(generated)) {
                    return generated;
                }
            } catch (StorageException ambiguousFailure) {
                return storage.find(generated.key()).orElseThrow(() -> ambiguousFailure);
            }
            return storage.find(generated.key()).orElseThrow(() ->
                    new NoSuchElementException("insert lost its uniqueness race but canonical row is absent"));
        });
    }

    public int inFlightCount() {
        return inFlight.size();
    }
}
