package me.alexisbinh.openlootr.paper.nms.v1_21_11;

import me.alexisbinh.openlootr.container.ResourceKey;
import me.alexisbinh.openlootr.paper.nms.VanillaParityBridge;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.criterion.LootTableTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** The only class allowed to name Minecraft internals; pinned to Paper 1.21.11. */
public final class Paper1211VanillaParityBridge implements VanillaParityBridge {
    private volatile boolean linked;

    @Override
    public void verifyLinkage() {
        try {
            // Resolve fields and exact erased method descriptors, not merely the owner classes.
            LootTableTrigger generateLoot = CriteriaTriggers.GENERATE_LOOT;
            if (generateLoot == null) {
                throw new NoSuchFieldException("CriteriaTriggers.GENERATE_LOOT is null");
            }
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            lookup.findVirtual(LootTableTrigger.class, "trigger", MethodType.methodType(
                    void.class, ServerPlayer.class, net.minecraft.resources.ResourceKey.class));
            lookup.findStatic(PiglinAi.class, "angerNearbyPiglins", MethodType.methodType(
                    void.class, ServerLevel.class, net.minecraft.world.entity.player.Player.class, boolean.class));
            linked = true;
        } catch (ReflectiveOperationException | LinkageError failure) {
            linked = false;
            throw new IllegalStateException("Paper 1.21.11 vanilla parity linkage verification failed", failure);
        }
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
