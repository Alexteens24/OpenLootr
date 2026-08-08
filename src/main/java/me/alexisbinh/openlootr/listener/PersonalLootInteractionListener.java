package me.alexisbinh.openlootr.listener;

import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.paper.service.PersonalLootService;
import me.alexisbinh.openlootr.paper.container.ContainerResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/** Thin event boundary: resolve/adopt/cancel, then dispatch the shared service. */
public final class PersonalLootInteractionListener implements Listener {
    private final ContainerResolver resolver;
    private final PersonalLootService service;

    public PersonalLootInteractionListener(ContainerResolver resolver, PersonalLootService service) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (!service.accepting() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        ContainerResolution resolution = resolver.resolve(event.getClickedBlock());
        if (resolution instanceof ContainerResolution.Ignored) {
            return;
        }
        event.setCancelled(true);
        resolution = adoptBlockIfNeeded(event, resolution);
        if (resolution instanceof ContainerResolution.Managed managed) {
            service.openBlock(event.getPlayer(), event.getClickedBlock().getLocation(), managed.descriptor());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!service.accepting() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ContainerResolution resolution = resolver.resolve(event.getRightClicked());
        if (resolution instanceof ContainerResolution.Ignored) {
            return;
        }
        event.setCancelled(true);
        if (resolution instanceof ContainerResolution.Candidate candidate) {
            resolution = resolver.adopt(event.getRightClicked(), candidate);
        }
        if (resolution instanceof ContainerResolution.Managed managed) {
            service.openEntity(event.getPlayer(), event.getRightClicked(), managed.descriptor());
        } else {
            sendUnavailable(event.getPlayer(), resolution);
        }
    }

    private ContainerResolution adoptBlockIfNeeded(PlayerInteractEvent event, ContainerResolution resolution) {
        if (resolution instanceof ContainerResolution.Candidate candidate) {
            resolution = resolver.adopt(event.getClickedBlock(), candidate);
        }
        if (!(resolution instanceof ContainerResolution.Managed)) {
            sendUnavailable(event.getPlayer(), resolution);
        }
        return resolution;
    }

    private static void sendUnavailable(Player player, ContainerResolution resolution) {
        String reason = resolution instanceof ContainerResolution.Broken broken
                ? broken.reason() : "identity adoption failed";
        player.sendMessage(Component.text("[OpenLootr] Container unavailable: " + reason));
    }
}
