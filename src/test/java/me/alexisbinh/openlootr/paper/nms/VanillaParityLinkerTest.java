package me.alexisbinh.openlootr.paper.nms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VanillaParityLinkerTest {
    @Test
    void linksEveryRequiredSymbolEagerly() {
        VanillaParityBridge bridge = VanillaParityLinker.link(
                "fixture", layout("trigger"), getClass().getClassLoader());

        assertEquals("linked:fixture/fixture", bridge.status());
    }

    @Test
    void linkedHandlesInvokeThroughTheirAdaptedDescriptors() {
        LinkedVanillaParityBridge bridge = (LinkedVanillaParityBridge) VanillaParityLinker.link(
                "fixture", layout("trigger"), getClass().getClassLoader());
        CraftPlayer player = new CraftPlayer();
        LootTableTrigger.calls = 0;
        PiglinAi.calls = 0;

        bridge.triggerGeneratedLoot(player, "minecraft", "chests/simple_dungeon");
        bridge.angerNearbyPiglins(player);

        assertEquals(1, LootTableTrigger.calls);
        assertSame(player.handle, LootTableTrigger.lastPlayer);
        assertSame(Registries.LOOT_TABLE, LootTableTrigger.lastKey.registry);
        assertEquals("minecraft", LootTableTrigger.lastKey.identifier.namespace);
        assertEquals("chests/simple_dungeon", LootTableTrigger.lastKey.identifier.value);
        assertEquals(1, PiglinAi.calls);
        assertSame(player.handle, PiglinAi.lastPlayer);
        assertSame(player.handle.level, PiglinAi.lastLevel);
        assertTrue(PiglinAi.lastBlockOpen);
    }

    @Test
    void missingExactMethodFailsDuringLinkage() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VanillaParityLinker.link("fixture", layout("renamedTrigger"),
                        getClass().getClassLoader()));

        assertTrue(failure.getMessage().contains("Vanilla parity linkage failed"));
        assertTrue(failure.getMessage().contains("fixture"));
    }

    private static LinkageLayout layout(String triggerMethod) {
        return new LinkageLayout(
                "fixture",
                CraftPlayer.class.getName(),
                ServerPlayer.class.getName(),
                ServerLevel.class.getName(),
                MinecraftPlayer.class.getName(),
                Identifier.class.getName(),
                ResourceKey.class.getName(),
                Registries.class.getName(),
                CriteriaTriggers.class.getName(),
                LootTableTrigger.class.getName(),
                PiglinAi.class.getName(),
                "getHandle",
                "level",
                "fromNamespaceAndPath",
                "create",
                "LOOT_TABLE",
                "GENERATE_LOOT",
                triggerMethod,
                "angerNearbyPiglins"
        );
    }

    public static class MinecraftPlayer { }
    public static final class ServerLevel { }
    public static final class ServerPlayer extends MinecraftPlayer {
        private final ServerLevel level = new ServerLevel();
        public ServerLevel level() { return level; }
    }
    public static final class CraftPlayer {
        private final ServerPlayer handle = new ServerPlayer();
        public ServerPlayer getHandle() { return handle; }
    }
    public static final class Identifier {
        private final String namespace;
        private final String value;

        private Identifier(String namespace, String value) {
            this.namespace = namespace;
            this.value = value;
        }

        public static Identifier fromNamespaceAndPath(String namespace, String value) {
            return new Identifier(namespace, value);
        }
    }
    public static final class ResourceKey {
        private final ResourceKey registry;
        private final Identifier identifier;

        public ResourceKey() {
            this(null, null);
        }

        private ResourceKey(ResourceKey registry, Identifier identifier) {
            this.registry = registry;
            this.identifier = identifier;
        }

        public static ResourceKey create(ResourceKey registry, Identifier identifier) {
            return new ResourceKey(registry, identifier);
        }
    }
    public static final class Registries {
        public static final ResourceKey LOOT_TABLE = new ResourceKey();
    }
    public static final class LootTableTrigger {
        private static int calls;
        private static ServerPlayer lastPlayer;
        private static ResourceKey lastKey;

        public void trigger(ServerPlayer player, ResourceKey key) {
            calls++;
            lastPlayer = player;
            lastKey = key;
        }
    }
    public static final class CriteriaTriggers {
        public static final LootTableTrigger GENERATE_LOOT = new LootTableTrigger();
    }
    public static final class PiglinAi {
        private static int calls;
        private static ServerLevel lastLevel;
        private static MinecraftPlayer lastPlayer;
        private static boolean lastBlockOpen;

        public static void angerNearbyPiglins(ServerLevel level, MinecraftPlayer player,
                                              boolean blockOpen) {
            calls++;
            lastLevel = level;
            lastPlayer = player;
            lastBlockOpen = blockOpen;
        }
    }
}
