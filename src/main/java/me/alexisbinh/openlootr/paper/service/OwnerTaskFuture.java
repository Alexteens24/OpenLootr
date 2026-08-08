package me.alexisbinh.openlootr.paper.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class OwnerTaskFuture {
    private OwnerTaskFuture() { }

    static <T> CompletableFuture<T> supply(ContainerTaskDispatcher dispatcher, Supplier<T> work,
                                           Supplier<? extends Throwable> retiredFailure) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(retiredFailure, "retiredFailure");
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            dispatcher.execute(() -> {
                try {
                    result.complete(work.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            }, () -> {
                try {
                    result.completeExceptionally(Objects.requireNonNull(retiredFailure.get(),
                            "retired failure supplier returned null"));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (Throwable dispatchFailure) {
            result.completeExceptionally(dispatchFailure);
        }
        return result;
    }
}
