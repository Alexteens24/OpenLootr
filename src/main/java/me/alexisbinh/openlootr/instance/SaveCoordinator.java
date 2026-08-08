package me.alexisbinh.openlootr.instance;

import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.LootStorage;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Coalesces each key to its latest encoded snapshot and persists monotonically with CAS. */
public final class SaveCoordinator {
    private static final Duration DEBOUNCE = Duration.ofMillis(250);
    private static final Duration DEGRADED_RETRY = Duration.ofSeconds(5);
    private static final long[] RETRY_MILLIS = {250, 500, 1_000, 2_000, 5_000};

    private final LootStorage storage;
    private final DbExecutor dbExecutor;
    private final SchedulerFacade scheduler;
    private final Logger logger;
    private final BiFunction<LootInstanceState, PersistenceHealth, CompletableFuture<Void>> degradedHandler;
    private final Consumer<LootInstanceState> cleanHandler;
    private final Set<InstanceKey> scheduled = ConcurrentHashMap.newKeySet();
    private final Set<InstanceKey> writing = ConcurrentHashMap.newKeySet();
    private final Map<InstanceKey, LootInstanceState> dirtyStates = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingMutations = new AtomicBoolean(true);
    private volatile boolean stopping;

    public SaveCoordinator(LootStorage storage, DbExecutor dbExecutor, SchedulerFacade scheduler,
                           Logger logger,
                           BiFunction<LootInstanceState, PersistenceHealth, CompletableFuture<Void>> degradedHandler,
                           Consumer<LootInstanceState> cleanHandler) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.degradedHandler = Objects.requireNonNull(degradedHandler, "degradedHandler");
        this.cleanHandler = Objects.requireNonNull(cleanHandler, "cleanHandler");
    }

    public void dirty(LootInstanceState state) {
        if (!acceptingMutations.get()) {
            return;
        }
        dirtyStates.put(state.key(), state);
        schedule(state, DEBOUNCE);
    }

    public void flush(LootInstanceState state) {
        dirtyStates.put(state.key(), state);
        if (!stopping) {
            writeLatest(state);
        }
    }

    private void schedule(LootInstanceState state, Duration delay) {
        if (!state.dirty() || stopping) {
            return;
        }
        if (scheduled.add(state.key())) {
            scheduler.executeAsyncLater(() -> {
                scheduled.remove(state.key());
                writeLatest(state);
            }, delay);
        }
    }

    private void writeLatest(LootInstanceState state) {
        if (!state.dirty() || !writing.add(state.key())) {
            return;
        }
        LootInstanceRecord snapshot = state.latestRecord();
        long expected = state.committedRevision();
        dbExecutor.supply(() -> storage.updateCas(snapshot, expected)).whenComplete((updated, failure) -> {
            writing.remove(state.key());
            if (failure == null && Boolean.TRUE.equals(updated)) {
                boolean recovered = state.committed(snapshot.revision());
                if (recovered) {
                    logger.info("Persistence recovered for {} at revision {}", state.key(), snapshot.revision());
                }
                if (state.dirty()) {
                    schedule(state, Duration.ZERO);
                } else {
                    dirtyStates.remove(state.key(), state);
                    cleanHandler.accept(state);
                }
                return;
            }
            if (failure == null) {
                logger.error("CAS conflict for {}; refusing to merge or overwrite persisted state", state.key());
                transitionOutOfHealthy(state, PersistenceHealth.QUARANTINED);
                return;
            } else {
                logger.error("Failed to persist {} revision {}", state.key(), snapshot.revision(), failure);
            }
            boolean degraded = state.failed();
            if (degraded) {
                transitionOutOfHealthy(state, PersistenceHealth.DEGRADED);
            } else {
                int index = Math.min(Math.max(0, state.consecutiveFailures() - 1), RETRY_MILLIS.length - 1);
                schedule(state, Duration.ofMillis(RETRY_MILLIS[index]));
            }
        });
    }

    private void transitionOutOfHealthy(LootInstanceState state, PersistenceHealth target) {
        if (!state.beginDegrading(target)) {
            if (target == PersistenceHealth.DEGRADED
                    && state.persistenceHealth() == PersistenceHealth.DEGRADED) {
                schedule(state, DEGRADED_RETRY);
            }
            return;
        }
        final CompletableFuture<Void> transition;
        try {
            transition = Objects.requireNonNull(degradedHandler.apply(state, target),
                    "degraded handler returned null");
        } catch (Throwable failure) {
            state.finishDegrading(target);
            logger.error("Failed to dispatch degraded transition for {}", state.key(), failure);
            if (target == PersistenceHealth.DEGRADED) {
                schedule(state, DEGRADED_RETRY);
            }
            return;
        }
        transition.whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.error("Failed to capture active session while degrading {}", state.key(), failure);
            }
            if (target == PersistenceHealth.DEGRADED) {
                schedule(state, DEGRADED_RETRY);
            }
        });
    }

    public void stopAccepting() { stopping = true; }

    public void flushAll() { dirtyStates.values().forEach(this::flush); }

    /** Stops new mutations and completes once every known latest revision is committed or the deadline expires. */
    public CompletableFuture<Boolean> drain(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        acceptingMutations.set(false);
        long deadline = System.nanoTime() + timeout.toNanos();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        flushAll();
        checkDrained(deadline, result);
        return result.whenComplete((ignored, failure) -> stopping = true);
    }

    private void checkDrained(long deadline, CompletableFuture<Boolean> result) {
        dirtyStates.entrySet().removeIf(entry -> !entry.getValue().dirty());
        if (dirtyStates.isEmpty() && writing.isEmpty()) {
            result.complete(true);
            return;
        }
        if (System.nanoTime() >= deadline) {
            result.complete(false);
            return;
        }
        flushAll();
        CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS)
                .execute(() -> checkDrained(deadline, result));
    }

    public int pendingCount() { return dirtyStates.size(); }

    public int writingCount() { return writing.size(); }

    public boolean acceptingMutations() { return acceptingMutations.get(); }
}
