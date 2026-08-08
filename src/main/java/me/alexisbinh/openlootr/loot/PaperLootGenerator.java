package me.alexisbinh.openlootr.loot;

import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
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

    public LootInstanceRecord generate(ContainerDescriptor descriptor, Location origin, java.util.UUID playerId,
                                       float luck, long personalSeed) {
        ItemStack[] combined = new ItemStack[descriptor.logicalSize()];
        LootContext context = new LootContext.Builder(origin.clone()).luck(luck).build();
        for (int index = 0; index < descriptor.lootSources().size(); index++) {
            var source = descriptor.lootSources().get(index);
            NamespacedKey key = NamespacedKey.fromString(source.lootTable().toString());
            LootTable table = key == null ? null : Bukkit.getLootTable(key);
            if (table == null) {
                throw new IllegalStateException("Loot table is unavailable: " + source.lootTable());
            }
            Inventory scratch = Bukkit.createInventory(null, source.size());
            long seed = descriptor.lootSources().size() == 1
                    ? personalSeed : SeedDerivation.sourceSeed(personalSeed, index);
            try (GenerationEventTracker.Scope scope = tracker.begin(table)) {
                table.fillInventory(scratch, new Random(seed), context);
                if (scope.cancelled()) {
                    throw new LootGenerationCancelledException();
                }
            }
            System.arraycopy(scratch.getContents(), 0, combined, source.slotOffset(), source.size());
        }
        long now = Instant.now().toEpochMilli();
        InstanceKey instanceKey = new InstanceKey(descriptor.containerId().orElseThrow(), playerId);
        return new LootInstanceRecord(instanceKey, descriptor.logicalSize(), ContainerCodec.CURRENT_VERSION,
                0, personalSeed, codec.encode(combined), now, now);
    }
}
