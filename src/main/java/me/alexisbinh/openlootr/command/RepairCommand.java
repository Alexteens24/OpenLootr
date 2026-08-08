package me.alexisbinh.openlootr.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.alexisbinh.openlootr.OpenLootrPlugin;
import me.alexisbinh.openlootr.container.BlockPosition;
import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.paper.container.ContainerRepairPlan;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RepairCommand {
    private static final long TTL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private RepairCommand() { }

    public static int plan(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("[OpenLootr] Repair requires a player."));
            return 0;
        }
        OpenLootrPlugin plugin = OpenLootrPlugin.instance();
        Block block = player.getTargetBlockExact(8);
        if (plugin == null || block == null) {
            player.sendMessage(Component.text("[OpenLootr] Look at a repairable double chest within 8 blocks."));
            return 0;
        }
        var plan = plugin.containerResolver().planRepair(block);
        if (plan.isEmpty()) {
            player.sendMessage(Component.text("[OpenLootr] No safe repair is available. Conflicting IDs and DB data are never merged."));
            return 0;
        }
        String nonce = Long.toUnsignedString(RANDOM.nextLong(), 36);
        PENDING.put(player.getUniqueId(), new Pending(nonce, plan.orElseThrow(),
                System.currentTimeMillis() + TTL_MILLIS));
        player.sendMessage(Component.text("[OpenLootr] Repair plan: restore only missing metadata on "
                + plan.orElseThrow().missingMember() + ". No inventory rows will be changed."));
        player.sendMessage(Component.text("Confirm within 30s: /openlootr repair confirm " + nonce));
        return 1;
    }

    public static int confirm(CommandSourceStack source, String nonce) {
        if (!(source.getSender() instanceof Player player)) {
            return 0;
        }
        Pending pending = PENDING.remove(player.getUniqueId());
        if (pending == null || !pending.nonce().equals(nonce)
                || System.currentTimeMillis() > pending.expiresAt()) {
            player.sendMessage(Component.text("[OpenLootr] Repair confirmation is invalid or expired."));
            return 0;
        }
        ContainerRepairPlan plan = pending.plan();
        Block lookedAt = player.getTargetBlockExact(8);
        if (lookedAt == null || !lookedAt.getWorld().getUID().equals(plan.worldId())
                || !plan.members().contains(position(lookedAt))) {
            player.sendMessage(Component.text("[OpenLootr] Keep looking at the exact double chest being repaired."));
            return 0;
        }
        OpenLootrPlugin plugin = OpenLootrPlugin.instance();
        if (plugin == null || Bukkit.getWorld(plan.worldId()) == null) {
            return 0;
        }
        ContainerResolution result = plugin.containerResolver().repair(lookedAt, plan);
        if (result instanceof ContainerResolution.Managed) {
            player.sendMessage(Component.text("[OpenLootr] Double chest metadata repaired and revalidated."));
            return 1;
        }
        player.sendMessage(Component.text("[OpenLootr] Repair aborted: "
                + ((ContainerResolution.Broken) result).reason()));
        return 0;
    }

    private static BlockPosition position(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    private record Pending(String nonce, ContainerRepairPlan plan, long expiresAt) { }
}
