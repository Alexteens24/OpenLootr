package me.alexisbinh.openlootr.instance;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InstanceCache {
    private final ConcurrentMap<InstanceKey, LootInstanceState> instances = new ConcurrentHashMap<>();

    public Optional<LootInstanceState> get(InstanceKey key) {
        return Optional.ofNullable(instances.get(key));
    }

    public LootInstanceState establish(LootInstanceRecord record) {
        return instances.computeIfAbsent(record.key(), ignored -> new LootInstanceState(record));
    }

    public void evictIfCleanAndClosed(LootInstanceState state) {
        if (!state.dirty() && !state.degraded() && state.openSessions() == 0) {
            instances.remove(state.key(), state);
        }
    }

    public int size() { return instances.size(); }
}
