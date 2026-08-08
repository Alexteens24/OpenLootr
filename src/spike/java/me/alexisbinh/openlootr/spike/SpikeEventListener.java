package me.alexisbinh.openlootr.spike;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.loot.Lootable;

import java.util.Map;

final class SpikeEventListener implements Listener {
    private final SpikeTrace trace;

    SpikeEventListener(SpikeTrace trace) {
        this.trace = trace;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractLowest(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block != null) {
            trace.record("loot-consumption", "interact-lowest", blockFields(block,
                    "block=" + event.useInteractedBlock() + ",item=" + event.useItemInHand()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractMonitor(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block != null) {
            trace.record("loot-consumption", "interact-monitor", blockFields(block,
                    "block=" + event.useInteractedBlock() + ",item=" + event.useItemInHand()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLootGenerate(LootGenerateEvent event) {
        trace.record("loot-generation", "loot-generate", Map.of(
                "table", event.getLootTable().getKey(),
                "plugin", event.isPlugin(),
                "cancelled", event.isCancelled(),
                "holder", event.getInventoryHolder() == null ? "null"
                        : event.getInventoryHolder().getClass().getName(),
                "items", event.getLoot().size(),
                "luck", event.getLootContext().getLuck()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        trace.record("loot-consumption", "inventory-move", Map.of(
                "source", event.getSource().getHolder() == null ? "null"
                        : event.getSource().getHolder().getClass().getName(),
                "destination", event.getDestination().getHolder() == null ? "null"
                        : event.getDestination().getHolder().getClass().getName(),
                "cancelled", event.isCancelled()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        trace.record("loot-consumption", "inventory-open", Map.of(
                "holder", event.getInventory().getHolder() == null ? "null"
                        : event.getInventory().getHolder().getClass().getName(),
                "cancelled", event.isCancelled()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        trace.record("pdc-lifecycle", "block-break", blockFields(event.getBlock(), event.isCancelled()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            trace.record("pdc-lifecycle", "entity-explosion", blockFields(block, event.isCancelled()));
        }
    }

    private static Map<String, Object> blockFields(Block block, Object eventResult) {
        BlockState state = block.getState(false);
        String table = state instanceof Lootable lootable && lootable.getLootTable() != null
                ? lootable.getLootTable().getKey().toString() : "null";
        return Map.of(
                "world", block.getWorld().getKey(),
                "position", block.getX() + "," + block.getY() + "," + block.getZ(),
                "type", block.getType(),
                "state", state.getClass().getName(),
                "lootTable", table,
                "eventResult", eventResult
        );
    }
}
