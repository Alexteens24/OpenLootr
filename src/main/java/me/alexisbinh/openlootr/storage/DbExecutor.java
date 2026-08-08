package me.alexisbinh.openlootr.storage;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class DbExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;

    public DbExecutor() {
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "OpenLootr-db");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    public <T> CompletableFuture<T> supply(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        return CompletableFuture.supplyAsync(work, executor);
    }

    public CompletableFuture<Void> run(Runnable work) {
        Objects.requireNonNull(work, "work");
        return CompletableFuture.runAsync(work, executor);
    }

    public int queuedTasks() {
        return executor.getQueue().size();
    }

    public boolean shutdown(Duration timeout) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
            executor.shutdownNow();
            return executor.awaitTermination(Math.min(1_000L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        shutdown(Duration.ofSeconds(10));
    }
}
