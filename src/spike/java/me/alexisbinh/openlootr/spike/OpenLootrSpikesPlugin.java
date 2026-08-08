package me.alexisbinh.openlootr.spike;

import me.alexisbinh.openlootr.codec.ContainerCodec;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public final class OpenLootrSpikesPlugin extends JavaPlugin {
    private SpikeTrace trace;
    private NamespacedKey metadataVersionKey;
    private NamespacedKey containerIdKey;
    private NamespacedKey lootTableKey;
    private NamespacedKey lootSeedKey;

    @Override
    public void onEnable() {
        trace = new SpikeTrace(this);
        metadataVersionKey = new NamespacedKey(this, "metadata_version");
        containerIdKey = new NamespacedKey(this, "container_id");
        lootTableKey = new NamespacedKey(this, "loot_table");
        lootSeedKey = new NamespacedKey(this, "source_loot_seed");
        getServer().getPluginManager().registerEvents(new SpikeEventListener(trace), this);
        trace.record("harness", "enable", Map.of("version", getPluginMeta().getVersion()));
        getSLF4JLogger().warn("OpenLootrSpikes is a development-only probe plugin; do not run it in production");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) {
            sender.sendMessage("[OpenLootrSpikes] trace=" + trace.output());
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This probe requires a player looking at a block.");
            return true;
        }
        return switch (action) {
            case "inspect" -> inspect(player);
            case "mark" -> mark(player);
            case "codec" -> codec(player);
            default -> false;
        };
    }

    private boolean inspect(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            player.sendMessage("No target block within 8 blocks.");
            return true;
        }
        BlockState state = target.getState(false);
        String table = state instanceof Lootable lootable && lootable.getLootTable() != null
                ? lootable.getLootTable().getKey().toString() : "null";
        String identity = state instanceof TileState tile
                ? tile.getPersistentDataContainer().get(containerIdKey, PersistentDataType.STRING) : null;
        Map<String, Object> fields = Map.of(
                "type", target.getType(),
                "position", target.getX() + "," + target.getY() + "," + target.getZ(),
                "lootTable", table,
                "identity", String.valueOf(identity)
        );
        trace.record("container-inspect", "inspect", fields);
        player.sendMessage("[OpenLootrSpikes] " + fields);
        return true;
    }

    private boolean mark(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target == null || !(target.getState(false) instanceof TileState tile)
                || !(tile instanceof Lootable lootable) || lootable.getLootTable() == null) {
            player.sendMessage("Target must be a lootable TileState with a loot table.");
            return true;
        }
        UUID identity = UUID.randomUUID();
        var pdc = tile.getPersistentDataContainer();
        pdc.set(metadataVersionKey, PersistentDataType.INTEGER, 1);
        pdc.set(containerIdKey, PersistentDataType.STRING, identity.toString());
        pdc.set(lootTableKey, PersistentDataType.STRING, lootable.getLootTable().getKey().toString());
        pdc.set(lootSeedKey, PersistentDataType.LONG, lootable.getSeed());
        boolean updated = tile.update(true, false);
        trace.record("pdc-lifecycle", "mark", Map.of(
                "identity", identity, "updated", updated, "type", target.getType()));
        player.sendMessage("[OpenLootrSpikes] marked " + identity + ", updated=" + updated);
        return true;
    }

    private boolean codec(Player player) {
        try {
            ItemStack[] input = new ItemStack[27];
            input[0] = player.getInventory().getItemInMainHand().clone();
            ContainerCodec codec = new ContainerCodec();
            byte[] encoded = codec.encode(input);
            ItemStack[] decoded = codec.decode(encoded, 27, ContainerCodec.CURRENT_VERSION);
            boolean equal = input[0].equals(decoded[0]);
            trace.record("codec", "round-trip", Map.of("bytes", encoded.length, "equal", equal));
            player.sendMessage("[OpenLootrSpikes] codec bytes=" + encoded.length + ", equal=" + equal);
        } catch (Exception exception) {
            trace.record("codec", "round-trip-failure", Map.of("error", exception.toString()));
            player.sendMessage("[OpenLootrSpikes] codec failed: " + exception.getMessage());
        }
        return true;
    }
}
