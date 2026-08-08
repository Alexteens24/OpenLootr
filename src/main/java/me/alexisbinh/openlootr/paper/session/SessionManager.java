package me.alexisbinh.openlootr.paper.session;

import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.instance.InstanceCache;
import me.alexisbinh.openlootr.instance.LootInstanceState;
import me.alexisbinh.openlootr.instance.PersistenceHealth;
import me.alexisbinh.openlootr.instance.SaveCoordinator;
import me.alexisbinh.openlootr.paper.menu.MenuFactory;
import me.alexisbinh.openlootr.paper.behavior.SessionLifecycleBehavior;
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
    private final SessionLifecycleBehavior behavior;
    private final Map<UUID, LootSession> sessions = new ConcurrentHashMap<>();
    private volatile SaveCoordinator saves;

    public SessionManager(MenuFactory menuFactory, ContainerCodec codec,
                          SchedulerFacade scheduler, InstanceCache cache) {
        this(menuFactory, codec, scheduler, cache, SessionLifecycleBehavior.NOOP);
    }

    public SessionManager(MenuFactory menuFactory, ContainerCodec codec,
                          SchedulerFacade scheduler, InstanceCache cache,
                          SessionLifecycleBehavior behavior) {
        this.menuFactory = Objects.requireNonNull(menuFactory, "menuFactory");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.behavior = Objects.requireNonNull(behavior, "behavior");
    }

    public void attachSaveCoordinator(SaveCoordinator saves) {
        if (this.saves != null) {
            throw new IllegalStateException("save coordinator already attached");
        }
        this.saves = Objects.requireNonNull(saves, "saves");
    }

    public void open(Player player, OpenAttemptId attempt, LootInstanceState state,
                     org.bukkit.inventory.ItemStack[] contents, Component title,
                     ContainerDescriptor descriptor, boolean created) {
        if (state.degraded()) {
            player.sendMessage(Component.text("[OpenLootr] This personal inventory is unavailable because saving failed."));
            return;
        }
        LootSession old = sessions.remove(player.getUniqueId());
        if (old != null) {
            snapshot(old, true);
            closed(old);
        }
        InventoryView view = menuFactory.open(player, state.containerSize(), contents, title);
        state.opened();
        LootSession session = LootSession.create(player.getUniqueId(), attempt,
                view.getTopInventory(), state, descriptor, created);
        sessions.put(player.getUniqueId(), session);
        behavior.opened(player, descriptor, created);
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
            closed(session);
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
            closed(session);
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
        }, () -> {
            if (sessions.remove(player.getUniqueId(), expected)) {
                closed(expected);
            }
        });
    }

    private boolean captureCurrentContents(LootSession session) {
        session.instance().touch();
        byte[] encoded = codec.encode(session.topInventory().getContents());
        if (Arrays.equals(encoded, session.instance().latestRecord().inventoryData())) {
            return false;
        }
        session.instance().replace(encoded);
        return true;
    }

    private void snapshot(LootSession session, boolean immediate) {
        if (!captureCurrentContents(session)) {
            return;
        }
        if (immediate) {
            requireSaves().flush(session.instance());
        } else {
            requireSaves().dirty(session.instance());
        }
    }

    public CompletableFuture<Void> degrade(LootInstanceState state, PersistenceHealth target) {
        if (target != PersistenceHealth.DEGRADED && target != PersistenceHealth.QUARANTINED) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("invalid degraded transition target: " + target));
        }
        LootSession session = sessions.values().stream()
                .filter(candidate -> candidate.instance() == state)
                .findFirst().orElse(null);
        if (session == null) {
            state.finishDegrading(target);
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null) {
            state.finishDegrading(target);
            if (sessions.remove(session.playerId(), session)) {
                closed(session);
            }
            completed.complete(null);
            return completed;
        }
        scheduler.executeFor(player, () -> {
            try {
                if (sessions.get(session.playerId()) == session) {
                    captureCurrentContents(session);
                    state.finishDegrading(target);
                    sessions.remove(session.playerId(), session);
                    closed(session);
                    player.closeInventory();
                    player.sendMessage(Component.text(target == PersistenceHealth.QUARANTINED
                            ? "[OpenLootr] Inventory quarantined after a persistence conflict."
                            : "[OpenLootr] Inventory closed while persistence recovers."));
                } else {
                    state.finishDegrading(target);
                }
                completed.complete(null);
            } catch (Throwable failure) {
                state.finishDegrading(target);
                if (sessions.remove(session.playerId(), session)) {
                    closed(session);
                }
                completed.completeExceptionally(failure);
            }
        }, () -> {
            state.finishDegrading(target);
            if (sessions.remove(session.playerId(), session)) {
                closed(session);
            }
            completed.complete(null);
        });
        return completed;
    }

    public CompletableFuture<Void> snapshotAll() {
        CompletableFuture<?>[] tasks = sessions.values().stream().map(session -> {
            CompletableFuture<Void> done = new CompletableFuture<>();
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null) {
                if (sessions.remove(session.playerId(), session)) {
                    closed(session);
                }
                done.complete(null);
            } else {
                scheduler.executeFor(player, () -> {
                    try {
                        if (sessions.remove(session.playerId(), session)) {
                            snapshot(session, true);
                            closed(session);
                        }
                        done.complete(null);
                    } catch (Throwable failure) {
                        done.completeExceptionally(failure);
                    }
                }, () -> {
                    if (sessions.remove(session.playerId(), session)) {
                        closed(session);
                    }
                    done.complete(null);
                });
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
                closed(session);
            }
        });
    }

    public int size() { return sessions.size(); }

    public void closeContainer(UUID containerId, String reason) {
        sessions.values().stream()
                .filter(session -> session.instance().key().containerId().equals(containerId))
                .forEach(session -> {
                    Player player = Bukkit.getPlayer(session.playerId());
                    if (player == null) {
                        if (sessions.remove(session.playerId(), session)) {
                            closed(session);
                        }
                        return;
                    }
                    scheduler.executeFor(player, () -> {
                        if (sessions.remove(session.playerId(), session)) {
                            snapshot(session, true);
                            closed(session);
                            player.closeInventory();
                            player.sendMessage(Component.text("[OpenLootr] " + reason));
                        }
                    }, () -> {
                        if (sessions.remove(session.playerId(), session)) {
                            closed(session);
                        }
                    });
                });
    }

    private void closed(LootSession session) {
        session.instance().closed();
        behavior.closed(session.descriptor());
    }

    private SaveCoordinator requireSaves() {
        return Objects.requireNonNull(saves, "save coordinator is not attached");
    }
}
