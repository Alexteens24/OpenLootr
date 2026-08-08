package me.alexisbinh.openlootr.paper.nms.v1_21_11;

import me.alexisbinh.openlootr.container.ResourceKey;
import me.alexisbinh.openlootr.paper.nms.VanillaParityBridge;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** The only class allowed to name Minecraft internals; pinned to Paper 1.21.11. */
public final class Paper1211VanillaParityBridge implements VanillaParityBridge {
    private volatile boolean linked;

    @Override
    public void verifyLinkage() {
        // Resolve both symbols eagerly so a Paper update fails startup, not a player's first chest.
        CriteriaTriggers.GENERATE_LOOT.getClass();
        PiglinAi.class.getName();
        linked = true;
    }

    @Override
    public void triggerGeneratedLoot(Player player, ResourceKey lootTable) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        Identifier id = Identifier.fromNamespaceAndPath(lootTable.namespace(), lootTable.value());
        CriteriaTriggers.GENERATE_LOOT.trigger(handle,
                net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, id));
    }

    @Override
    public void angerNearbyPiglins(Player player) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        PiglinAi.angerNearbyPiglins(handle.level(), handle, true);
    }

    @Override
    public String status() {
        return linked ? "linked:paper-1.21.11" : "unverified";
    }
}
