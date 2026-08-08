package me.alexisbinh.openlootr.instance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InFlightRegistryTest {
    @Test
    void fiftyCallersShareOneOperation() {
        InFlightRegistry<String, String> registry = new InFlightRegistry<>();
        CompletableFuture<String> gate = new CompletableFuture<>();
        AtomicInteger starts = new AtomicInteger();
        List<CompletableFuture<String>> calls = new ArrayList<>();

        for (int index = 0; index < 50; index++) {
            calls.add(registry.runOrJoin("container/player", () -> {
                starts.incrementAndGet();
                return gate;
            }));
        }

        assertEquals(1, starts.get());
        calls.forEach(call -> assertSame(calls.getFirst(), call));
        gate.complete("ready");
        calls.forEach(call -> assertEquals("ready", call.join()));
        assertEquals(0, registry.size());
    }
}
