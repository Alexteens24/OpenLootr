package me.alexisbinh.openlootr.instance;

import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InstanceCache {
    public static final Duration DEFAULT_IDLE_TTL = Duration.ofSeconds(60);

    private final ConcurrentMap<InstanceKey, LootInstanceState> instances = new ConcurrentHashMap<>();
    private final long idleMillis;

    public InstanceCache() {
        this(DEFAULT_IDLE_TTL);
    }

    public InstanceCache(Duration idleTtl) {
        if (idleTtl.isNegative() || idleTtl.isZero()) {
            throw new IllegalArgumentException("idle TTL must be positive");
        }
        idleMillis = idleTtl.toMillis();
    }

    public Optional<LootInstanceState> get(InstanceKey key) {
        LootInstanceState state = instances.get(key);
        if (state != null) {
            state.touch();
        }
        return Optional.ofNullable(state);
    }

    public LootInstanceState establish(LootInstanceRecord record) {
        LootInstanceState state = instances.computeIfAbsent(record.key(), ignored -> new LootInstanceState(record));
        state.touch();
        return state;
    }

    public void evictIfCleanAndClosed(LootInstanceState state) {
        if (state.lastAccessAt() <= System.currentTimeMillis() - idleMillis && evictable(state)) {
            instances.remove(state.key(), state);
        }
    }

    public int evictIdle() {
        long cutoff = System.currentTimeMillis() - idleMillis;
        int before = instances.size();
        instances.forEach((key, state) -> {
            if (state.lastAccessAt() <= cutoff && evictable(state)) {
                instances.remove(key, state);
            }
        });
        return before - instances.size();
    }

    public int count(PersistenceHealth health) {
        return (int) instances.values().stream()
                .filter(state -> state.persistenceHealth() == health).count();
    }

    public Optional<LootInstanceState> peek(InstanceKey key) {
        return Optional.ofNullable(instances.get(key));
    }

    private static boolean evictable(LootInstanceState state) {
        return !state.dirty() && state.persistenceHealth() == PersistenceHealth.HEALTHY
                && state.openSessions() == 0;
    }

    public int size() { return instances.size(); }
}
