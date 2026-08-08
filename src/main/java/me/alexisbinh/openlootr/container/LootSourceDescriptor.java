package me.alexisbinh.openlootr.container;

import java.util.Objects;

public record LootSourceDescriptor(ResourceKey lootTable, long physicalSeed, int slotOffset, int size) {
    public LootSourceDescriptor {
        Objects.requireNonNull(lootTable, "lootTable");
        if (slotOffset < 0 || size <= 0 || size % 9 != 0) {
            throw new IllegalArgumentException("invalid loot source slot range");
        }
    }
}
