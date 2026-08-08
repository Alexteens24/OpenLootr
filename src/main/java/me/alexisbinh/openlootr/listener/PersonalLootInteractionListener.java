package me.alexisbinh.openlootr.listener;

import me.alexisbinh.openlootr.codec.CodecException;
import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.paper.container.ContainerResolver;
import me.alexisbinh.openlootr.instance.FirstOpenService;
import me.alexisbinh.openlootr.instance.InstanceCache;
import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import me.alexisbinh.openlootr.instance.LootInstanceState;
import me.alexisbinh.openlootr.loot.LootGenerationCancelledException;
import me.alexisbinh.openlootr.loot.PaperLootGenerator;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.session.OpenAttemptId;
import me.alexisbinh.openlootr.paper.session.SessionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PersonalLootInteractionListener implements Listener {
    private final ContainerResolver resolver;
    private final FirstOpenService firstOpen;
    private final PaperLootGenerator generator;
    private final ContainerCodec codec;
    private final InstanceCache cache;
    private final SessionManager sessions;
    private final SchedulerFacade scheduler;
    private final Logger logger;
    private final Map<UUID, OpenAttemptId> attempts = new ConcurrentHashMap<>();
    private volatile boolean accepting = true;

    public PersonalLootInteractionListener(ContainerResolver resolver, FirstOpenService firstOpen,
                                           PaperLootGenerator generator, ContainerCodec codec,
                                           InstanceCache cache, SessionManager sessions,
                                           SchedulerFacade scheduler, Logger logger) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.firstOpen = Objects.requireNonNull(firstOpen, "firstOpen");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!accepting || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        ContainerResolution resolution = resolver.resolve(event.getClickedBlock());
        if (resolution instanceof ContainerResolution.Ignored) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (resolution instanceof ContainerResolution.Broken broken) {
            player.sendMessage(Component.text("[OpenLootr] Container unavailable: " + broken.reason()));
            return;
        }
        if (resolution instanceof ContainerResolution.Candidate candidate) {
            resolution = resolver.adopt(event.getClickedBlock(), candidate);
        }
        if (!(resolution instanceof ContainerResolution.Managed managed)) {
            String reason = resolution instanceof ContainerResolution.Broken broken
                    ? broken.reason() : "identity adoption failed";
            player.sendMessage(Component.text("[OpenLootr] Container unavailable: " + reason));
            return;
        }
        var luckAttribute = player.getAttribute(Attribute.LUCK);
        beginOpen(player, managed.descriptor(), luckAttribute == null ? 0.0F : (float) luckAttribute.getValue());
    }

    private void beginOpen(Player player, ContainerDescriptor descriptor, float luck) {
        UUID playerId = player.getUniqueId();
        OpenAttemptId attempt = OpenAttemptId.create();
        attempts.put(playerId, attempt);
        InstanceKey key = new InstanceKey(descriptor.containerId().orElseThrow(), playerId);
        Location location = location(descriptor);
        if (location == null) {
            fail(player, attempt, "The container world is unavailable.", null);
            return;
        }
        CompletableFuture<LootInstanceRecord> established = cache.get(key)
                .map(state -> CompletableFuture.completedFuture(state.latestRecord()))
                .orElseGet(() -> firstOpen.establish(key,
                        ignored -> generateOnRegion(descriptor, playerId, luck, location)));
        established.whenComplete((record, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                String message = cause instanceof LootGenerationCancelledException
                        ? "Loot generation was cancelled by another plugin."
                        : "Personal loot could not be loaded safely.";
                fail(player, attempt, message, cause instanceof LootGenerationCancelledException ? null : cause);
                return;
            }
            scheduler.executeAt(location, () -> {
                ContainerResolution live = resolver.resolve(location.getBlock());
                if (!(live instanceof ContainerResolution.Managed managed)
                        || !sameIdentity(descriptor, managed.descriptor())) {
                    fail(player, attempt, "The container changed while loading.", null);
                    return;
                }
                LootInstanceState state = cache.establish(record);
                scheduler.executeFor(player, () -> openIfCurrent(player, attempt, descriptor, state),
                        () -> attempts.remove(playerId, attempt));
            });
        });
    }

    private CompletableFuture<LootInstanceRecord> generateOnRegion(
            ContainerDescriptor expected, UUID playerId, float luck, Location location
    ) {
        CompletableFuture<LootInstanceRecord> generated = new CompletableFuture<>();
        scheduler.executeAt(location, () -> {
            try {
                ContainerResolution live = resolver.resolve(location.getBlock());
                if (!(live instanceof ContainerResolution.Managed managed)
                        || !sameIdentity(expected, managed.descriptor())) {
                    throw new IllegalStateException("container changed before loot generation");
                }
                long personalSeed = ThreadLocalRandom.current().nextLong();
                generated.complete(generator.generate(managed.descriptor(), location.getWorld(),
                        playerId, luck, personalSeed));
            } catch (Throwable failure) {
                generated.completeExceptionally(failure);
            }
        });
        return generated;
    }

    private void openIfCurrent(Player player, OpenAttemptId attempt,
                               ContainerDescriptor descriptor, LootInstanceState state) {
        if (!player.isOnline() || !attempt.equals(attempts.get(player.getUniqueId()))) {
            return;
        }
        try {
            var record = state.latestRecord();
            if (record.containerSize() != descriptor.logicalSize()) {
                throw new CodecException("Stored size does not match physical logical size");
            }
            var contents = codec.decode(record.inventoryData(), descriptor.logicalSize(), record.codecVersion());
            if (!attempts.remove(player.getUniqueId(), attempt)) {
                return;
            }
            sessions.open(player, attempt, state, contents,
                    Component.text(descriptor.kind() == me.alexisbinh.openlootr.container.ContainerKind.BARREL
                            ? "Personal Barrel" : "Personal Chest"));
        } catch (CodecException failure) {
            logger.error("Refusing to open undecodable personal inventory {}", state.key(), failure);
            player.sendMessage(Component.text("[OpenLootr] Stored inventory data is invalid; nothing was overwritten."));
        }
    }

    private void fail(Player player, OpenAttemptId attempt, String message, Throwable failure) {
        scheduler.executeFor(player, () -> {
            if (attempts.remove(player.getUniqueId(), attempt)) {
                player.sendMessage(Component.text("[OpenLootr] " + message));
            }
        }, () -> attempts.remove(player.getUniqueId(), attempt));
        if (failure != null) {
            logger.error("Open attempt {} failed for {}", attempt.value(), player.getUniqueId(), failure);
        }
    }

    private static boolean sameIdentity(ContainerDescriptor expected, ContainerDescriptor actual) {
        return expected.containerId().equals(actual.containerId())
                && expected.worldId().equals(actual.worldId())
                && expected.position().equals(actual.position())
                && expected.logicalSize() == actual.logicalSize()
                && expected.sourceLootTable().equals(actual.sourceLootTable());
    }

    private static Location location(ContainerDescriptor descriptor) {
        World world = Bukkit.getWorld(descriptor.worldId());
        return world == null ? null : new Location(world, descriptor.position().x(),
                descriptor.position().y(), descriptor.position().z());
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public void stopAccepting() { accepting = false; }
}
