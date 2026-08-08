package me.alexisbinh.openlootr.instance;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InFlightRegistry<K, V> {
    private final ConcurrentHashMap<K, CompletableFuture<V>> operations = new ConcurrentHashMap<>();

    public CompletableFuture<V> runOrJoin(K key, Supplier<CompletableFuture<V>> starter) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(starter, "starter");

        CompletableFuture<V> candidate = new CompletableFuture<>();
        CompletableFuture<V> existing = operations.putIfAbsent(key, candidate);
        if (existing != null) {
            return existing;
        }

        final CompletableFuture<V> started;
        try {
            started = Objects.requireNonNull(starter.get(), "starter returned null");
        } catch (Throwable throwable) {
            candidate.completeExceptionally(throwable);
            operations.remove(key, candidate);
            return candidate;
        }

        started.whenComplete((value, failure) -> {
            if (failure == null) {
                candidate.complete(value);
            } else {
                candidate.completeExceptionally(failure);
            }
            operations.remove(key, candidate);
        });
        return candidate;
    }

    public int size() {
        return operations.size();
    }
}
