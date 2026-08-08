package me.alexisbinh.openlootr.container;

import me.alexisbinh.openlootr.instance.LootInstanceRecord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable descriptor safe to carry across region/entity/database scheduler boundaries. */
public record ContainerDescriptor(
        Optional<UUID> containerId,
        ContainerLocator locator,
        ContainerKind kind,
        int logicalSize,
        List<LootSourceDescriptor> lootSources,
        int metadataVersion
) {
    public ContainerDescriptor {
        containerId = Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(kind, "kind");
        LootInstanceRecord.validateSize(logicalSize);
        lootSources = List.copyOf(Objects.requireNonNull(lootSources, "lootSources"));
        if (lootSources.isEmpty()) {
            throw new IllegalArgumentException("at least one loot source is required");
        }
        int covered = lootSources.stream().mapToInt(LootSourceDescriptor::size).sum();
        if (covered != logicalSize) {
            throw new IllegalArgumentException("loot sources must cover the logical inventory exactly");
        }
        if (metadataVersion < 0) {
            throw new IllegalArgumentException("metadataVersion must not be negative");
        }
    }

    public UUID worldId() { return locator.worldId(); }

    public BlockPosition position() {
        if (locator instanceof BlockLocator block) {
            return block.owner();
        }
        throw new IllegalStateException("entity container has no block position");
    }

    public ResourceKey sourceLootTable() { return lootSources.getFirst().lootTable(); }

    public long sourceLootSeed() { return lootSources.getFirst().physicalSeed(); }
}
