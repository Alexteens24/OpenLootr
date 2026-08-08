package me.alexisbinh.openlootr.paper.behavior;

import me.alexisbinh.openlootr.container.BlockLocator;
import me.alexisbinh.openlootr.container.BlockPosition;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerKind;
import me.alexisbinh.openlootr.paper.nms.VanillaParityBridge;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Ref-counted physical presentation and selected vanilla side effects. */
public final class PaperContainerBehavior implements SessionLifecycleBehavior {
    private final SchedulerFacade scheduler;
    private final VanillaParityBridge parity;
    private final ConcurrentMap<UUID, Integer> viewers = new ConcurrentHashMap<>();

    public PaperContainerBehavior(SchedulerFacade scheduler, VanillaParityBridge parity) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.parity = Objects.requireNonNull(parity, "parity");
    }

    @Override
    public void opened(Player player, ContainerDescriptor descriptor, boolean created) {
        Statistic openStatistic = switch (descriptor.kind()) {
            case CHEST, DOUBLE_CHEST -> Statistic.CHEST_OPENED;
            case BARREL -> Statistic.OPEN_BARREL;
            // Vanilla storage-minecart statistic behavior remains a live Phase-10 evidence gate.
            case STORAGE_MINECART -> null;
        };
        if (openStatistic != null) {
            player.incrementStatistic(openStatistic);
        }
        if (created) {
            descriptor.lootSources().forEach(source -> parity.triggerGeneratedLoot(player, source.lootTable()));
        }
        if (descriptor.kind() == ContainerKind.CHEST || descriptor.kind() == ContainerKind.DOUBLE_CHEST
                || descriptor.kind() == ContainerKind.BARREL) {
            parity.angerNearbyPiglins(player);
        }
        if (descriptor.containerId().isPresent()
                && viewers.merge(descriptor.containerId().orElseThrow(), 1, Integer::sum) == 1) {
            setLids(descriptor, true);
        }
    }

    @Override
    public void closed(ContainerDescriptor descriptor) {
        if (descriptor.containerId().isEmpty()) {
            return;
        }
        UUID id = descriptor.containerId().orElseThrow();
        Integer remaining = viewers.compute(id, (ignored, count) -> count == null || count <= 1 ? null : count - 1);
        if (remaining == null) {
            setLids(descriptor, false);
        }
    }

    private void setLids(ContainerDescriptor descriptor, boolean open) {
        if (!(descriptor.locator() instanceof BlockLocator locator)) {
            return;
        }
        World world = Bukkit.getWorld(locator.worldId());
        if (world == null) {
            return;
        }
        Location owner = location(world, locator.owner());
        scheduler.executeAt(owner, () -> locator.members().forEach(position -> {
            if (world.getBlockAt(position.x(), position.y(), position.z()).getState(false) instanceof Lidded lidded) {
                if (open) {
                    lidded.open();
                } else {
                    lidded.close();
                }
            }
        }));
    }

    private static Location location(World world, BlockPosition position) {
        return new Location(world, position.x(), position.y(), position.z());
    }

    public int trackedContainers() { return viewers.size(); }
}
