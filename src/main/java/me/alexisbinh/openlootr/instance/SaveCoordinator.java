package me.alexisbinh.openlootr.instance;

import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.LootStorage;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Consumer<LootInstanceState> degradedHandler;
    private final Set<InstanceKey> scheduled = ConcurrentHashMap.newKeySet();
    private final Set<InstanceKey> writing = ConcurrentHashMap.newKeySet();
    private final Map<InstanceKey, LootInstanceState> dirtyStates = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    public SaveCoordinator(LootStorage storage, DbExecutor dbExecutor, SchedulerFacade scheduler,
                           Logger logger, Consumer<LootInstanceState> degradedHandler) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.degradedHandler = Objects.requireNonNull(degradedHandler, "degradedHandler");
    }

    public void dirty(LootInstanceState state) {
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
                state.committed(snapshot.revision());
                if (state.dirty()) {
                    schedule(state, Duration.ZERO);
                } else {
                    dirtyStates.remove(state.key(), state);
                }
                return;
            }
            if (failure == null) {
                logger.error("CAS conflict for {}; refusing to merge or overwrite persisted state", state.key());
                state.markDegraded();
                degradedHandler.accept(state);
                return;
            } else {
                logger.error("Failed to persist {} revision {}", state.key(), snapshot.revision(), failure);
            }
            boolean degraded = state.failed();
            if (degraded) {
                degradedHandler.accept(state);
                schedule(state, DEGRADED_RETRY);
            } else {
                int index = Math.min(Math.max(0, state.consecutiveFailures() - 1), RETRY_MILLIS.length - 1);
                schedule(state, Duration.ofMillis(RETRY_MILLIS[index]));
            }
        });
    }

    public void stopAccepting() { stopping = true; }

    public void flushAll() { dirtyStates.values().forEach(this::flush); }

    public int pendingCount() { return dirtyStates.size(); }
}
