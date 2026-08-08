package me.alexisbinh.openlootr.instance;

import java.util.Arrays;
import java.util.Objects;

public record LootInstanceRecord(
        InstanceKey key,
        int containerSize,
        int codecVersion,
        long revision,
        long generationSeed,
        byte[] inventoryData,
        long createdAt,
        long updatedAt
) {
    public LootInstanceRecord {
        Objects.requireNonNull(key, "key");
        validateSize(containerSize);
        if (codecVersion <= 0) {
            throw new IllegalArgumentException("codecVersion must be positive");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        inventoryData = Arrays.copyOf(Objects.requireNonNull(inventoryData, "inventoryData"), inventoryData.length);
    }

    @Override
    public byte[] inventoryData() {
        return Arrays.copyOf(inventoryData, inventoryData.length);
    }

    public static void validateSize(int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("container size must be a multiple of 9 from 9 through 54");
        }
    }
}
