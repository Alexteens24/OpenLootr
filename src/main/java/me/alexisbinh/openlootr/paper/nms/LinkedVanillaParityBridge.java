package me.alexisbinh.openlootr.paper.nms;

import me.alexisbinh.openlootr.container.ResourceKey;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Immutable bridge containing only handles and linked constants; no version branching at call sites. */
final class LinkedVanillaParityBridge implements VanillaParityBridge {
    private final String runtimeVersion;
    private final String layoutId;
    private final MethodHandle getHandle;
    private final MethodHandle createIdentifier;
    private final MethodHandle createResourceKey;
    private final MethodHandle triggerGeneratedLoot;
    private final MethodHandle getLevel;
    private final MethodHandle angerNearbyPiglins;
    private final Object lootTableRegistry;
    private final Object generateLootTrigger;

    LinkedVanillaParityBridge(String runtimeVersion, String layoutId,
                              MethodHandle getHandle, MethodHandle createIdentifier,
                              MethodHandle createResourceKey, MethodHandle triggerGeneratedLoot,
                              MethodHandle getLevel, MethodHandle angerNearbyPiglins,
                              Object lootTableRegistry, Object generateLootTrigger) {
        this.runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        this.layoutId = Objects.requireNonNull(layoutId, "layoutId");
        this.getHandle = Objects.requireNonNull(getHandle, "getHandle");
        this.createIdentifier = Objects.requireNonNull(createIdentifier, "createIdentifier");
        this.createResourceKey = Objects.requireNonNull(createResourceKey, "createResourceKey");
        this.triggerGeneratedLoot = Objects.requireNonNull(triggerGeneratedLoot, "triggerGeneratedLoot");
        this.getLevel = Objects.requireNonNull(getLevel, "getLevel");
        this.angerNearbyPiglins = Objects.requireNonNull(angerNearbyPiglins, "angerNearbyPiglins");
        this.lootTableRegistry = Objects.requireNonNull(lootTableRegistry, "lootTableRegistry");
        this.generateLootTrigger = Objects.requireNonNull(generateLootTrigger, "generateLootTrigger");
    }

    @Override
    public void triggerGeneratedLoot(Player player, ResourceKey lootTable) {
        triggerGeneratedLoot(player, lootTable.namespace(), lootTable.value());
    }

    void triggerGeneratedLoot(Object player, String namespace, String value) {
        try {
            Object serverPlayer = (Object) getHandle.invokeExact(player);
            Object identifier = (Object) createIdentifier.invokeExact(
                    namespace, value);
            Object key = (Object) createResourceKey.invokeExact(lootTableRegistry, identifier);
            triggerGeneratedLoot.invokeExact(generateLootTrigger, serverPlayer, key);
        } catch (Throwable failure) {
            throw invocationFailure("generated-loot criterion", failure);
        }
    }

    @Override
    public void angerNearbyPiglins(Player player) {
        angerNearbyPiglins((Object) player);
    }

    void angerNearbyPiglins(Object player) {
        try {
            Object serverPlayer = (Object) getHandle.invokeExact(player);
            Object level = (Object) getLevel.invokeExact(serverPlayer);
            angerNearbyPiglins.invokeExact(level, serverPlayer, true);
        } catch (Throwable failure) {
            throw invocationFailure("piglin anger", failure);
        }
    }

    @Override
    public String status() {
        return "linked:" + runtimeVersion + "/" + layoutId;
    }

    private IllegalStateException invocationFailure(String operation, Throwable failure) {
        return new IllegalStateException("Linked vanilla parity operation failed: " + operation
                + " on Minecraft " + runtimeVersion + " using " + layoutId, failure);
    }
}
