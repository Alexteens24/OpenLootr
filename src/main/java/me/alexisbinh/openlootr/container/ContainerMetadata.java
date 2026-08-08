package me.alexisbinh.openlootr.container;

import java.util.Objects;
import java.util.UUID;

public record ContainerMetadata(
        int version,
        UUID containerId,
        ResourceKey lootTable,
        long sourceLootSeed
) {
    public static final int CURRENT_VERSION = 1;

    public ContainerMetadata {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported metadata version: " + version);
        }
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(lootTable, "lootTable");
    }
}
