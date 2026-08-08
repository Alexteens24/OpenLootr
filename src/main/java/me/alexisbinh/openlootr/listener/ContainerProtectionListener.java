package me.alexisbinh.openlootr.listener;

import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.paper.container.ContainerResolver;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public final class ContainerProtectionListener implements Listener {
    private final ContainerResolver resolver;

    public ContainerProtectionListener(ContainerResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedContainer(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedContainer);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedContainer);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (protectedInventory(event.getSource()) || protectedInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean protectedInventory(Inventory inventory) {
        Location location = inventory.getLocation();
        return location != null && protectedContainer(location.getBlock());
    }

    private boolean protectedContainer(Block block) {
        ContainerResolution resolution = resolver.resolve(block);
        return resolution instanceof ContainerResolution.Candidate
                || resolution instanceof ContainerResolution.Managed
                || resolution instanceof ContainerResolution.Broken;
    }
}
