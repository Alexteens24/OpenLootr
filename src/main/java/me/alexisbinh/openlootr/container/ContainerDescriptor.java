package me.alexisbinh.openlootr.container;

import me.alexisbinh.openlootr.instance.LootInstanceRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable data safe to carry across scheduler and database boundaries. */
public record ContainerDescriptor(
        Optional<UUID> containerId,
        UUID worldId,
        BlockPosition position,
        ContainerKind kind,
        int logicalSize,
        ResourceKey sourceLootTable,
        long sourceLootSeed,
        int metadataVersion
) {
    public ContainerDescriptor {
        containerId = Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(kind, "kind");
        LootInstanceRecord.validateSize(logicalSize);
        Objects.requireNonNull(sourceLootTable, "sourceLootTable");
        if (metadataVersion < 0) {
            throw new IllegalArgumentException("metadataVersion must not be negative");
        }
    }
}
