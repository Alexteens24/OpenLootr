package me.alexisbinh.openlootr.instance;

import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.LootStorage;
import me.alexisbinh.openlootr.storage.StorageHealth;
import me.alexisbinh.openlootr.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FirstOpenServiceTest {
    private final DbExecutor executor = new DbExecutor();

    @AfterEach
    void closeExecutor() {
        executor.shutdown(Duration.ofSeconds(2));
    }

    @Test
    void concurrentFirstOpenGeneratesAndInsertsOnce() {
        MemoryStorage storage = new MemoryStorage();
        FirstOpenService service = new FirstOpenService(storage, executor);
        InstanceKey key = new InstanceKey(UUID.randomUUID(), UUID.randomUUID());
        CompletableFuture<LootInstanceRecord> generationGate = new CompletableFuture<>();
        AtomicInteger generations = new AtomicInteger();

        CompletableFuture<LootInstanceRecord> first = service.establish(key, ignored -> {
            generations.incrementAndGet();
            return generationGate;
        });
        CompletableFuture<LootInstanceRecord> second = service.establish(key, ignored -> {
            generations.incrementAndGet();
            return generationGate;
        });

        LootInstanceRecord generated = record(key);
        generationGate.complete(generated);
        assertSame(generated, first.join());
        assertSame(generated, second.join());
        assertEquals(1, generations.get());
        assertEquals(1, storage.inserts.get());
    }

    @Test
    void ambiguousCommitReadsCanonicalRowInsteadOfRegenerating() {
        MemoryStorage storage = new MemoryStorage();
        storage.failAfterInsert = true;
        FirstOpenService service = new FirstOpenService(storage, executor);
        InstanceKey key = new InstanceKey(UUID.randomUUID(), UUID.randomUUID());
        AtomicInteger generations = new AtomicInteger();

        LootInstanceRecord result = service.establish(key, ignored -> {
            generations.incrementAndGet();
            return CompletableFuture.completedFuture(record(key));
        }).join();

        assertEquals(key, result.key());
        assertEquals(1, generations.get());
        assertEquals(1, storage.rows.size());
    }

    private static LootInstanceRecord record(InstanceKey key) {
        return new LootInstanceRecord(key, 27, 1, 0, 123L, new byte[]{1}, 1L, 1L);
    }

    private static final class MemoryStorage implements LootStorage {
        private final ConcurrentHashMap<InstanceKey, LootInstanceRecord> rows = new ConcurrentHashMap<>();
        private final AtomicInteger inserts = new AtomicInteger();
        private boolean failAfterInsert;

        @Override public void initialize() { }
        @Override public Optional<LootInstanceRecord> find(InstanceKey key) { return Optional.ofNullable(rows.get(key)); }
        @Override public boolean insertFirst(LootInstanceRecord record) {
            boolean inserted = rows.putIfAbsent(record.key(), record) == null;
            if (inserted) {
                inserts.incrementAndGet();
                if (failAfterInsert) {
                    throw new StorageException("simulated lost commit acknowledgement");
                }
            }
            return inserted;
        }
        @Override public boolean updateCas(LootInstanceRecord record, long expectedRevision) { return false; }
        @Override public long countByContainer(UUID containerId) {
            return rows.keySet().stream().filter(key -> key.containerId().equals(containerId)).count();
        }
        @Override public StorageHealth health() { return StorageHealth.unavailable("memory"); }
        @Override public void close() { }
    }
}
