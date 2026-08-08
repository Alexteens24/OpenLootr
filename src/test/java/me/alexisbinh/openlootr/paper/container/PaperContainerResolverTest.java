package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.ContainerResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PaperContainerResolverTest {
    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PaperContainerResolver resolver;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("OpenLootr");
        world = server.addSimpleWorld("world");
        resolver = new PaperContainerResolver(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void normalChestIsIgnoredWithoutMutation() {
        Block block = block(Material.CHEST, 0);
        assertInstanceOf(ContainerResolution.Ignored.class, resolver.resolve(block));
        Chest chest = (Chest) block.getState();
        assertEquals(0, chest.getPersistentDataContainer().getKeys().size());
    }

    @Test
    void validRightClickAdoptionProducesStableManagedIdentity() {
        Block block = block(Material.CHEST, 1);
        Chest chest = (Chest) block.getState();
        chest.setLootTable(Bukkit.getLootTable(LootTables.SIMPLE_DUNGEON.getKey()));
        chest.setSeed(42L);
        chest.update(true, false);

        ContainerResolution.Candidate candidate = assertInstanceOf(
                ContainerResolution.Candidate.class, resolver.resolve(block));
        ContainerResolution.Managed adopted = assertInstanceOf(
                ContainerResolution.Managed.class, resolver.adopt(block, candidate));
        ContainerResolution.Managed loaded = assertInstanceOf(
                ContainerResolution.Managed.class, resolver.resolve(block));

        assertEquals(adopted.descriptor().containerId(), loaded.descriptor().containerId());
        assertEquals(42L, loaded.descriptor().sourceLootSeed());
        assertEquals(LootTables.SIMPLE_DUNGEON.getKey().toString(),
                loaded.descriptor().sourceLootTable().toString());
    }

    @Test
    void partialMetadataFailsClosedEvenWithoutLootTable() {
        Block block = block(Material.BARREL, 2);
        Barrel barrel = (Barrel) block.getState();
        barrel.getPersistentDataContainer().set(new NamespacedKey(plugin, "metadata_version"),
                PersistentDataType.INTEGER, 1);
        barrel.update(true, false);

        assertInstanceOf(ContainerResolution.Broken.class, resolver.resolve(block));
    }

    private Block block(Material material, int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(material);
        return block;
    }
}
