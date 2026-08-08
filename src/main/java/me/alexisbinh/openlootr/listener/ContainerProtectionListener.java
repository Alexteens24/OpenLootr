package me.alexisbinh.openlootr.listener;

import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.paper.container.ContainerResolver;
import me.alexisbinh.openlootr.paper.feedback.PlayerFeedback;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import me.alexisbinh.openlootr.paper.session.SessionManager;

import java.util.Objects;

public final class ContainerProtectionListener implements Listener {
    private final ContainerResolver resolver;
    private final SessionManager sessions;
    private final PlayerFeedback feedback;

    public ContainerProtectionListener(ContainerResolver resolver, SessionManager sessions,
                                       PlayerFeedback feedback) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedContainer(event.getBlock())) {
            event.setCancelled(true);
            feedback.cannotBreak(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ContainerResolution resolution = resolver.resolve(event.getBlockPlaced());
        if ((resolution instanceof ContainerResolution.Managed managed
                && managed.descriptor().kind() == me.alexisbinh.openlootr.container.ContainerKind.DOUBLE_CHEST)
                || resolution instanceof ContainerResolution.Broken) {
            event.setCancelled(true);
            feedback.cannotMerge(event.getPlayer());
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (protectedEntity(event.getVehicle())) {
            event.setCancelled(true);
            if (event.getAttacker() instanceof Player player) {
                feedback.cannotBreak(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            return;
        }
        ContainerResolution resolution = resolver.resolve(event.getEntity());
        if (resolution instanceof ContainerResolution.Managed managed) {
            sessions.closeContainer(managed.descriptor().containerId().orElseThrow());
        }
    }

    private boolean protectedInventory(Inventory inventory) {
        if (inventory.getHolder() instanceof Entity entity && protectedEntity(entity)) {
            return true;
        }
        Location location = inventory.getLocation();
        return location != null && protectedContainer(location.getBlock());
    }

    private boolean protectedEntity(Entity entity) {
        ContainerResolution resolution = resolver.resolve(entity);
        return resolution instanceof ContainerResolution.Candidate
                || resolution instanceof ContainerResolution.Managed
                || resolution instanceof ContainerResolution.Broken;
    }

    private boolean protectedContainer(Block block) {
        ContainerResolution resolution = resolver.resolve(block);
        return resolution instanceof ContainerResolution.Candidate
                || resolution instanceof ContainerResolution.Managed
                || resolution instanceof ContainerResolution.Broken;
    }
}
