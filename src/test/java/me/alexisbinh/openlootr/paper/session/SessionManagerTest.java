package me.alexisbinh.openlootr.paper.session;

import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.instance.InstanceCache;
import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import me.alexisbinh.openlootr.instance.LootInstanceState;
import me.alexisbinh.openlootr.instance.PersistenceHealth;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.session.OpenAttemptId;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void degradeCapturesMutationThatHasNotReachedNextTickSnapshot() throws Exception {
        Player player = server.addPlayer();
        ContainerCodec codec = new ContainerCodec();
        ItemStack[] empty = new ItemStack[27];
        LootInstanceState state = new LootInstanceState(new LootInstanceRecord(
                new InstanceKey(UUID.randomUUID(), player.getUniqueId()), 27, 1, 0, 9L,
                codec.encode(empty), 1L, 1L));
        SessionManager manager = new SessionManager((viewer, size, contents, title) -> {
            var inventory = Bukkit.createInventory(null, size, title);
            inventory.setContents(contents);
            return viewer.openInventory(inventory);
        }, codec, new ImmediateScheduler(), new InstanceCache());

        manager.open(player, OpenAttemptId.create(), state, empty, Component.text("Personal Chest"));
        player.getOpenInventory().getTopInventory().setItem(0, new ItemStack(Material.DIAMOND));
        assertTrue(state.beginDegrading(PersistenceHealth.DEGRADED));

        manager.degrade(state, PersistenceHealth.DEGRADED).get();

        ItemStack[] captured = codec.decode(state.latestRecord().inventoryData(), 27, 1);
        assertEquals(Material.DIAMOND, captured[0].getType());
        assertEquals(PersistenceHealth.DEGRADED, state.persistenceHealth());
        assertEquals(0, state.openSessions());
        assertEquals(0, manager.size());
    }

    private static final class ImmediateScheduler implements SchedulerFacade {
        @Override public void executeGlobal(Runnable task) { task.run(); }
        @Override public void executeAt(Location location, Runnable task) { task.run(); }
        @Override public void executeFor(Entity entity, Runnable task, Runnable retired) { task.run(); }
        @Override public void executeAsync(Runnable task) { task.run(); }
        @Override public void executeAsyncLater(Runnable task, Duration delay) { task.run(); }
        @Override public void cancelPluginTasks() { }
        @Override public void stopAccepting() { }
        @Override public boolean isFolia() { return false; }
    }
}
