package me.alexisbinh.openlootr.container;

import java.util.Objects;
import java.util.UUID;

public record ContainerMetadata(
        int version,
        UUID containerId,
        ResourceKey lootTable,
        long sourceLootSeed
) {
    public static final int SINGLE_VERSION = 1;
    public static final int DOUBLE_VERSION = 2;
    public static final int CURRENT_VERSION = DOUBLE_VERSION;

    public ContainerMetadata {
        if (version != SINGLE_VERSION && version != DOUBLE_VERSION) {
            throw new IllegalArgumentException("unsupported metadata version: " + version);
        }
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(lootTable, "lootTable");
    }
}
