package me.alexisbinh.openlootr.instance;

import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.storage.DbExecutor;
import me.alexisbinh.openlootr.storage.LootStorage;
import me.alexisbinh.openlootr.storage.StorageHealth;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveCoordinatorTest {
    private final DbExecutor executor = new DbExecutor();

    @AfterEach
    void close() { executor.shutdown(Duration.ofSeconds(2)); }

    @Test
    void drainCommitsLatestRevisionNotEveryIntermediateSnapshot() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        LootInstanceRecord initial = record(0, new byte[]{0});
        storage.rows.put(initial.key(), initial);
        LootInstanceState state = new LootInstanceState(initial);
        SaveCoordinator saves = new SaveCoordinator(storage, executor, new ImmediateScheduler(),
                LoggerFactory.getLogger("test"), (ignored, target) -> java.util.concurrent.CompletableFuture.completedFuture(null),
                ignored -> { });

        state.replace(new byte[]{1});
        state.replace(new byte[]{2});
        state.replace(new byte[]{3});
        saves.dirty(state);

        assertTrue(saves.drain(Duration.ofSeconds(2)).get());
        assertEquals(3, storage.rows.get(initial.key()).revision());
        assertEquals(1, storage.updates);
        assertEquals(0, saves.pendingCount());
    }

    @Test
    void casConflictQuarantinesInsteadOfOverwritingNewerDatabaseState() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        LootInstanceRecord initial = record(0, new byte[]{0});
        storage.rows.put(initial.key(), new LootInstanceRecord(initial.key(), 27, 1, 8,
                2L, new byte[]{8}, 1L, 8L));
        LootInstanceState state = new LootInstanceState(initial);
        state.replace(new byte[]{1});
        SaveCoordinator saves = new SaveCoordinator(storage, executor, new ImmediateScheduler(),
                LoggerFactory.getLogger("test"), (instance, target) -> {
                    instance.finishDegrading(target);
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }, ignored -> { });

        saves.flush(state);
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (state.persistenceHealth() != PersistenceHealth.QUARANTINED
                && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertEquals(PersistenceHealth.QUARANTINED, state.persistenceHealth());
        assertEquals(8, storage.rows.get(initial.key()).revision());
        assertEquals(0, storage.updates);
    }

    private static LootInstanceRecord record(long revision, byte[] bytes) {
        return new LootInstanceRecord(new InstanceKey(UUID.randomUUID(), UUID.randomUUID()),
                27, 1, revision, 2L, bytes, 1L, 1L);
    }

    private static final class MemoryStorage implements LootStorage {
        private final Map<InstanceKey, LootInstanceRecord> rows = new ConcurrentHashMap<>();
        private volatile int updates;
        @Override public void initialize() { }
        @Override public Optional<LootInstanceRecord> find(InstanceKey key) { return Optional.ofNullable(rows.get(key)); }
        @Override public boolean insertFirst(LootInstanceRecord record) { return rows.putIfAbsent(record.key(), record) == null; }
        @Override public boolean updateCas(LootInstanceRecord record, long expected) {
            synchronized (rows) {
                LootInstanceRecord old = rows.get(record.key());
                if (old == null || old.revision() != expected) return false;
                rows.put(record.key(), record);
                updates++;
                return true;
            }
        }
        @Override public long countByContainer(UUID id) { return 0; }
        @Override public StorageHealth health() { return StorageHealth.unavailable("memory"); }
        @Override public void close() { }
    }

    private static final class ImmediateScheduler implements SchedulerFacade {
        @Override public void executeGlobal(Runnable task) { task.run(); }
        @Override public void executeAt(Location location, Runnable task) { task.run(); }
        @Override public void executeFor(Entity entity, Runnable task, Runnable retired) { task.run(); }
        @Override public void executeAsync(Runnable task) { task.run(); }
        @Override public void executeAsyncLater(Runnable task, Duration delay) { task.run(); }
        @Override public void cancelPluginTasks() { }
        @Override public void stopAccepting() { }
        @Override public boolean isFolia() { return false; }
    }
}
