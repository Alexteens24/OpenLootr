package me.alexisbinh.openlootr.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.alexisbinh.openlootr.OpenLootrPlugin;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerResolution;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class InspectCommand {
    private InspectCommand() {
    }

    public static int execute(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("[OpenLootr] This command requires a player."));
            return 0;
        }
        OpenLootrPlugin plugin = OpenLootrPlugin.instance();
        if (plugin == null || !plugin.isEnabled()) {
            player.sendMessage(Component.text("[OpenLootr] Plugin is unavailable."));
            return 0;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null) {
            player.sendMessage(Component.text("[OpenLootr] No block in range."));
            return 0;
        }
        ContainerResolution resolution = plugin.containerResolver().resolve(block);
        player.sendMessage(Component.text("[OpenLootr] " + resolution.getClass().getSimpleName()));
        if (resolution instanceof ContainerResolution.Ignored ignored) {
            player.sendMessage(Component.text("Reason: " + ignored.reason()));
            return 1;
        }
        if (resolution instanceof ContainerResolution.Broken broken) {
            player.sendMessage(Component.text("Invariant failure: " + broken.reason()));
            return 1;
        }
        ContainerDescriptor descriptor = resolution instanceof ContainerResolution.Managed managed
                ? managed.descriptor() : ((ContainerResolution.Candidate) resolution).descriptor();
        player.sendMessage(Component.text("Type: " + descriptor.kind() + ", size=" + descriptor.logicalSize()));
        player.sendMessage(Component.text("Location: " + descriptor.worldId() + " / " + descriptor.position()));
        player.sendMessage(Component.text("Loot table: " + descriptor.sourceLootTable()
                + ", source seed=" + descriptor.sourceLootSeed()));
        if (descriptor.containerId().isEmpty()) {
            player.sendMessage(Component.text("Container UUID: not adopted"));
            return 1;
        }
        var id = descriptor.containerId().orElseThrow();
        player.sendMessage(Component.text("Container UUID: " + id));
        plugin.dbExecutor().supply(() -> plugin.storage().countByContainer(id))
                .whenComplete((count, failure) -> plugin.scheduler().executeFor(player, () -> {
                    if (failure == null) {
                        player.sendMessage(Component.text("Personal instances: " + count));
                    } else {
                        player.sendMessage(Component.text("Personal instances: unavailable (see server log)"));
                        plugin.getSLF4JLogger().error("Inspect count failed for {}", id, failure);
                    }
                }, () -> { }));
        return 1;
    }
}
