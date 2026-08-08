package me.alexisbinh.openlootr.paper.service;

import me.alexisbinh.openlootr.codec.CodecException;
import me.alexisbinh.openlootr.codec.ContainerCodec;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.loot.LootGenerationCancelledException;
import me.alexisbinh.openlootr.loot.PaperLootGenerator;
import me.alexisbinh.openlootr.instance.EstablishedInstance;
import me.alexisbinh.openlootr.instance.FirstOpenService;
import me.alexisbinh.openlootr.instance.InstanceCache;
import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import me.alexisbinh.openlootr.instance.LootInstanceState;
import me.alexisbinh.openlootr.paper.container.ContainerResolver;
import me.alexisbinh.openlootr.paper.session.SessionManager;
import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import me.alexisbinh.openlootr.session.OpenAttemptId;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** One first-open pipeline shared by block and entity listeners. */
public final class PersonalLootService {
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

    public PersonalLootService(ContainerResolver resolver, FirstOpenService firstOpen,
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

    public boolean accepting() { return accepting; }

    public void stopAccepting() { accepting = false; }

    public int inFlightCount() { return firstOpen.inFlightCount(); }

    public void openBlock(Player player, Location blockLocation, ContainerDescriptor descriptor) {
        Location origin = blockLocation.clone().add(0.5, 0.5, 0.5);
        beginOpen(player, descriptor, origin,
                task -> scheduler.executeAt(blockLocation, task),
                () -> resolver.resolve(blockLocation.getBlock()));
    }

    public void openEntity(Player player, Entity entity, ContainerDescriptor descriptor) {
        Location origin = entity.getLocation().clone();
        beginOpen(player, descriptor, origin,
                task -> scheduler.executeFor(entity, task, () -> { }),
                () -> resolver.resolve(entity));
    }

    private void beginOpen(Player player, ContainerDescriptor descriptor, Location origin,
                           ContainerDispatcher owner, Supplier<ContainerResolution> liveResolver) {
        UUID playerId = player.getUniqueId();
        OpenAttemptId attempt = OpenAttemptId.create();
        attempts.put(playerId, attempt);
        InstanceKey key = new InstanceKey(descriptor.containerId().orElseThrow(), playerId);
        var luckAttribute = player.getAttribute(Attribute.LUCK);
        float luck = luckAttribute == null ? 0.0F : (float) luckAttribute.getValue();
        CompletableFuture<EstablishedInstance> established = cache.get(key)
                .map(state -> CompletableFuture.completedFuture(
                        new EstablishedInstance(state.latestRecord(), false)))
                .orElseGet(() -> firstOpen.establish(key,
                        ignored -> generateOnOwner(descriptor, playerId, luck, origin, owner, liveResolver)));
        established.whenComplete((result, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                fail(player, attempt, cause instanceof LootGenerationCancelledException
                        ? "Loot generation was cancelled by another plugin."
                        : "Personal loot could not be loaded safely.",
                        cause instanceof LootGenerationCancelledException ? null : cause);
                return;
            }
            owner.execute(() -> {
                ContainerResolution live = liveResolver.get();
                if (!(live instanceof ContainerResolution.Managed managed)
                        || !sameIdentity(descriptor, managed.descriptor())) {
                    fail(player, attempt, "The container changed while loading.", null);
                    return;
                }
                LootInstanceState state = cache.establish(result.record());
                scheduler.executeFor(player,
                        () -> openIfCurrent(player, attempt, descriptor, state, result.created()),
                        () -> attempts.remove(playerId, attempt));
            });
        });
    }

    private CompletableFuture<LootInstanceRecord> generateOnOwner(
            ContainerDescriptor expected, UUID playerId, float luck, Location origin,
            ContainerDispatcher owner, Supplier<ContainerResolution> liveResolver
    ) {
        CompletableFuture<LootInstanceRecord> generated = new CompletableFuture<>();
        owner.execute(() -> {
            try {
                ContainerResolution live = liveResolver.get();
                if (!(live instanceof ContainerResolution.Managed managed)
                        || !sameIdentity(expected, managed.descriptor())) {
                    throw new IllegalStateException("container changed before loot generation");
                }
                generated.complete(generator.generate(managed.descriptor(), origin, playerId, luck,
                        ThreadLocalRandom.current().nextLong()));
            } catch (Throwable failure) {
                generated.completeExceptionally(failure);
            }
        });
        return generated;
    }

    private void openIfCurrent(Player player, OpenAttemptId attempt, ContainerDescriptor descriptor,
                               LootInstanceState state, boolean created) {
        if (!player.isOnline() || !attempt.equals(attempts.get(player.getUniqueId()))) {
            return;
        }
        try {
            LootInstanceRecord record = state.latestRecord();
            if (record.containerSize() != descriptor.logicalSize()) {
                throw new CodecException("Stored size does not match physical logical size");
            }
            var contents = codec.decode(record.inventoryData(), descriptor.logicalSize(), record.codecVersion());
            if (!attempts.remove(player.getUniqueId(), attempt)) {
                return;
            }
            sessions.open(player, attempt, state, contents, title(descriptor), descriptor, created);
        } catch (CodecException failure) {
            state.corrupt(failure.getMessage());
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

    private static Component title(ContainerDescriptor descriptor) {
        return Component.text(switch (descriptor.kind()) {
            case BARREL -> "Personal Barrel";
            case STORAGE_MINECART -> "Personal Minecart";
            default -> "Personal Chest";
        });
    }

    private static boolean sameIdentity(ContainerDescriptor expected, ContainerDescriptor actual) {
        return expected.containerId().equals(actual.containerId())
                && expected.locator().equals(actual.locator())
                && expected.kind() == actual.kind()
                && expected.logicalSize() == actual.logicalSize()
                && expected.lootSources().equals(actual.lootSources());
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface ContainerDispatcher {
        void execute(Runnable task);
    }
}
