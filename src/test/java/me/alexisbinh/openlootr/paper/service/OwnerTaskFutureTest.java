package me.alexisbinh.openlootr.paper.service;

import me.alexisbinh.openlootr.instance.InFlightRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnerTaskFutureTest {
    @Test
    void retiredOwnerCompletesFutureAndReleasesInFlightKey() {
        InFlightRegistry<String, Integer> registry = new InFlightRegistry<>();
        ContainerTaskDispatcher retired = (task, retiredCallback) -> retiredCallback.run();

        CompletionException failure = assertThrows(CompletionException.class, () -> registry
                .runOrJoin("minecart/player", () -> OwnerTaskFuture.supply(retired, () -> 1,
                        () -> new ContainerUnavailableException("retired")))
                .join());

        assertInstanceOf(ContainerUnavailableException.class, failure.getCause());
        assertEquals(0, registry.size());
    }

    @Test
    void ownerStateIsCapturedOnlyWhenOwnedTaskActuallyRuns() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        ContainerTaskDispatcher deferred = (task, retired) -> scheduled.set(task);
        AtomicInteger movingOrigin = new AtomicInteger(4);

        var result = OwnerTaskFuture.supply(deferred, movingOrigin::get,
                () -> new ContainerUnavailableException("retired"));
        movingOrigin.set(19);
        scheduled.get().run();

        assertEquals(19, result.join());
    }

    @Test
    void dispatchFailureIsAlsoTerminal() {
        ContainerTaskDispatcher broken = (task, retired) -> {
            throw new IllegalStateException("scheduler rejected task");
        };

        CompletionException failure = assertThrows(CompletionException.class,
                () -> OwnerTaskFuture.supply(broken, () -> 1,
                        () -> new ContainerUnavailableException("retired")).join());

        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }
}
