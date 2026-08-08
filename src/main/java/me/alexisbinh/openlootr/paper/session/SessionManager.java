package me.alexisbinh.openlootr.paper.session;

import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.instance.InstanceCache;
import me.alexisbinh.openlootr.instance.LootInstanceState;
import me.alexisbinh.openlootr.instance.SaveCoordinator;
import me.alexisbinh.openlootr.paper.menu.MenuFactory;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.session.OpenAttemptId;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager implements Listener {
    private final MenuFactory menuFactory;
    private final ContainerCodec codec;
    private final SchedulerFacade scheduler;
    private final InstanceCache cache;
    private final Map<UUID, LootSession> sessions = new ConcurrentHashMap<>();
    private volatile SaveCoordinator saves;

    public SessionManager(MenuFactory menuFactory, ContainerCodec codec,
                          SchedulerFacade scheduler, InstanceCache cache) {
        this.menuFactory = Objects.requireNonNull(menuFactory, "menuFactory");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public void attachSaveCoordinator(SaveCoordinator saves) {
        if (this.saves != null) {
            throw new IllegalStateException("save coordinator already attached");
        }
        this.saves = Objects.requireNonNull(saves, "saves");
    }

    public void open(Player player, OpenAttemptId attempt, LootInstanceState state,
                     org.bukkit.inventory.ItemStack[] contents, Component title) {
        if (state.degraded()) {
            player.sendMessage(Component.text("[OpenLootr] This personal inventory is unavailable because saving failed."));
            return;
        }
        LootSession old = sessions.remove(player.getUniqueId());
        if (old != null) {
            snapshot(old, true);
            old.instance().closed();
        }
        InventoryView view = menuFactory.open(player, state.containerSize(), contents, title);
        state.opened();
        sessions.put(player.getUniqueId(), LootSession.create(player.getUniqueId(), attempt,
                view.getTopInventory(), state));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            snapshotNextTick(player, event.getView());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            snapshotNextTick(player, event.getView());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        LootSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && event.getInventory() == session.topInventory()
                && sessions.remove(session.playerId(), session)) {
            snapshot(session, true);
            session.instance().closed();
            cache.evictIfCleanAndClosed(session.instance());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { closePlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) { closePlayer(event.getPlayer()); }

    private void closePlayer(Player player) {
        LootSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            snapshot(session, true);
            session.instance().closed();
        }
    }

    private void snapshotNextTick(Player player, InventoryView eventView) {
        LootSession expected = sessions.get(player.getUniqueId());
        if (expected == null || eventView.getTopInventory() != expected.topInventory()) {
            return;
        }
        scheduler.executeFor(player, () -> {
            LootSession current = sessions.get(player.getUniqueId());
            if (current == expected && player.getOpenInventory().getTopInventory() == expected.topInventory()) {
                snapshot(expected, false);
            }
        }, () -> sessions.remove(player.getUniqueId(), expected));
    }

    private void snapshot(LootSession session, boolean immediate) {
        byte[] encoded = codec.encode(session.topInventory().getContents());
        if (Arrays.equals(encoded, session.instance().latestRecord().inventoryData())) {
            return;
        }
        session.instance().replace(encoded);
        if (immediate) {
            requireSaves().flush(session.instance());
        } else {
            requireSaves().dirty(session.instance());
        }
    }

    public void degrade(LootInstanceState state) {
        sessions.values().stream().filter(session -> session.instance() == state).forEach(session -> {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null) {
                scheduler.executeFor(player, () -> {
                    LootSession removed = sessions.remove(session.playerId());
                    if (removed == session) {
                        player.closeInventory();
                        player.sendMessage(Component.text("[OpenLootr] Inventory closed: persistence is degraded."));
                    }
                }, () -> sessions.remove(session.playerId(), session));
            }
        });
    }

    public CompletableFuture<Void> snapshotAll() {
        CompletableFuture<?>[] tasks = sessions.values().stream().map(session -> {
            CompletableFuture<Void> done = new CompletableFuture<>();
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null) {
                done.complete(null);
            } else {
                scheduler.executeFor(player, () -> {
                    try {
                        if (sessions.remove(session.playerId(), session)) {
                            snapshot(session, true);
                            session.instance().closed();
                        }
                        done.complete(null);
                    } catch (Throwable failure) {
                        done.completeExceptionally(failure);
                    }
                }, () -> done.complete(null));
            }
            return done;
        }).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(tasks);
    }

    /** Paper disable runs on its owning main thread, so scheduling a next-tick task would deadlock. */
    public void snapshotAllNow() {
        sessions.values().forEach(session -> {
            if (sessions.remove(session.playerId(), session)) {
                snapshot(session, true);
                session.instance().closed();
            }
        });
    }

    public int size() { return sessions.size(); }

    private SaveCoordinator requireSaves() {
        return Objects.requireNonNull(saves, "save coordinator is not attached");
    }
}
