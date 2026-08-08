package me.alexisbinh.openlootr.loot;

import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.time.Instant;
import java.util.Objects;
import java.util.Random;

public final class PaperLootGenerator {
    private final ContainerCodec codec;
    private final GenerationEventTracker tracker;

    public PaperLootGenerator(ContainerCodec codec, GenerationEventTracker tracker) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    public LootInstanceRecord generate(ContainerDescriptor descriptor, World world, java.util.UUID playerId,
                                       float luck, long personalSeed) {
        NamespacedKey key = NamespacedKey.fromString(descriptor.sourceLootTable().toString());
        LootTable table = key == null ? null : Bukkit.getLootTable(key);
        if (table == null) {
            throw new IllegalStateException("Loot table is unavailable: " + descriptor.sourceLootTable());
        }
        Inventory scratch = Bukkit.createInventory(null, descriptor.logicalSize());
        LootContext context = new LootContext.Builder(blockCenter(world, descriptor)).luck(luck).build();
        try (GenerationEventTracker.Scope scope = tracker.begin(table)) {
            table.fillInventory(scratch, new Random(personalSeed), context);
            if (scope.cancelled()) {
                throw new LootGenerationCancelledException();
            }
        }
        long now = Instant.now().toEpochMilli();
        InstanceKey instanceKey = new InstanceKey(descriptor.containerId().orElseThrow(), playerId);
        return new LootInstanceRecord(instanceKey, descriptor.logicalSize(), ContainerCodec.CURRENT_VERSION,
                0, personalSeed, codec.encode(scratch.getContents()), now, now);
    }

    private static org.bukkit.Location blockCenter(World world, ContainerDescriptor descriptor) {
        return new org.bukkit.Location(world, descriptor.position().x() + 0.5,
                descriptor.position().y() + 0.5, descriptor.position().z() + 0.5);
    }
}
