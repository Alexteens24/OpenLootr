package me.alexisbinh.openlootr.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.alexisbinh.openlootr.OpenLootrPlugin;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InspectCommand {
    private InspectCommand() { }

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
        Target target = target(player);
        if (target == null) {
            player.sendMessage(Component.text("[OpenLootr] No block or entity in range."));
            return 0;
        }
        ContainerResolution resolution = target.resolve(plugin);
        if (!(resolution instanceof ContainerResolution.Managed managed)) {
            showNonManaged(plugin, player, target, resolution);
            return 1;
        }
        ContainerDescriptor descriptor = managed.descriptor();
        var id = descriptor.containerId().orElseThrow();
        InstanceKey key = new InstanceKey(id, player.getUniqueId());
        plugin.dbExecutor().supply(() -> new StorageInspection(
                        plugin.storage().countByContainer(id), plugin.storage().find(key)))
                .whenComplete((stored, failure) -> plugin.scheduler().executeFor(player, () -> {
                    if (failure != null) {
                        plugin.getSLF4JLogger().error("Inspect query failed for {}", id, failure);
                    }
                    List<Component> lines = descriptorLines(target, descriptor);
                    lines.add(Component.text("Personal instances: "
                            + (failure == null ? stored.count() : "unavailable")));
                    lines.add(Component.text("Current player row: "
                            + (failure == null && stored.current().isPresent()
                            ? row(stored.current().orElseThrow()) : "none/unavailable")));
                    var cached = plugin.instanceCache().peek(key);
                    lines.add(Component.text("Cache: " + cached.map(state -> "present, rev="
                                    + state.currentRevision() + ", committed=" + state.committedRevision()
                                    + ", health=" + state.persistenceHealth()
                                    + (state.faultReason() == null ? "" : ", fault=" + state.faultReason()))
                            .orElse("absent")));
                    plugin.inspectorPresenter().show(player, Component.text("OpenLootr Inspect"), lines);
                }, () -> { }));
        return 1;
    }

    private static void showNonManaged(OpenLootrPlugin plugin, Player player, Target target,
                                       ContainerResolution resolution) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Target: " + target.description()));
        lines.add(Component.text("Resolution: " + resolution.getClass().getSimpleName()));
        if (resolution instanceof ContainerResolution.Ignored ignored) {
            lines.add(Component.text("Reason: " + ignored.reason()));
        } else if (resolution instanceof ContainerResolution.Broken broken) {
            lines.add(Component.text("Invariant failure: " + broken.reason()));
        } else if (resolution instanceof ContainerResolution.Candidate candidate) {
            lines.addAll(descriptorLines(target, candidate.descriptor()));
            lines.add(Component.text("Container UUID: not adopted"));
        }
        plugin.inspectorPresenter().show(player, Component.text("OpenLootr Inspect"), lines);
    }

    private static List<Component> descriptorLines(Target target, ContainerDescriptor descriptor) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Target: " + target.description()));
        lines.add(Component.text("Type: " + descriptor.kind() + ", size=" + descriptor.logicalSize()));
        lines.add(Component.text("Locator: " + descriptor.locator()));
        lines.add(Component.text("Container UUID: " + descriptor.containerId().map(Object::toString).orElse("none")));
        lines.add(Component.text("Metadata version: " + descriptor.metadataVersion()));
        for (int index = 0; index < descriptor.lootSources().size(); index++) {
            var source = descriptor.lootSources().get(index);
            lines.add(Component.text("Source " + index + ": " + source.lootTable()
                    + ", seed=" + source.physicalSeed() + ", slots=" + source.slotOffset()
                    + ".." + (source.slotOffset() + source.size() - 1)));
        }
        lines.add(Component.text("Automation: blocked"));
        return lines;
    }

    private static String row(LootInstanceRecord record) {
        return "rev=" + record.revision() + ", codec=" + record.codecVersion()
                + ", bytes=" + record.inventoryData().length;
    }

    private static Target target(Player player) {
        var entityHit = player.rayTraceEntities(8);
        if (entityHit != null && entityHit.getHitEntity() != null) {
            return new Target(null, entityHit.getHitEntity());
        }
        Block block = player.getTargetBlockExact(8);
        return block == null ? null : new Target(block, null);
    }

    private record StorageInspection(long count, Optional<LootInstanceRecord> current) { }

    private record Target(Block block, Entity entity) {
        ContainerResolution resolve(OpenLootrPlugin plugin) {
            return block != null ? plugin.containerResolver().resolve(block)
                    : plugin.containerResolver().resolve(entity);
        }

        String description() {
            return block != null ? block.getType() + " @ " + block.getX() + ',' + block.getY() + ',' + block.getZ()
                    : entity.getType() + " / " + entity.getUniqueId();
        }
    }
}
