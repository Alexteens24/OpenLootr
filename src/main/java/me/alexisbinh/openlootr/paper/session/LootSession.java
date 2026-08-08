package me.alexisbinh.openlootr.paper.session;

import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.instance.LootInstanceState;
import me.alexisbinh.openlootr.session.OpenAttemptId;
import org.bukkit.inventory.Inventory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LootSession(UUID playerId, OpenAttemptId attemptId, Inventory topInventory,
                          LootInstanceState instance, ContainerDescriptor descriptor,
                          boolean created, long openedAt) {
    public LootSession {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(topInventory, "topInventory");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    public static LootSession create(UUID playerId, OpenAttemptId attemptId,
                                     Inventory inventory, LootInstanceState instance,
                                     ContainerDescriptor descriptor, boolean created) {
        return new LootSession(playerId, attemptId, inventory, instance, descriptor,
                created, Instant.now().toEpochMilli());
    }
}
