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
import me.alexisbinh.openlootr.paper.feedback.PlayerFeedback;
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
    private final PlayerFeedback feedback;
    private final Logger logger;
    private final Map<UUID, OpenAttemptId> attempts = new ConcurrentHashMap<>();
    private volatile boolean accepting = true;

    public PersonalLootService(ContainerResolver resolver, FirstOpenService firstOpen,
                               PaperLootGenerator generator, ContainerCodec codec,
                               InstanceCache cache, SessionManager sessions,
                               SchedulerFacade scheduler, PlayerFeedback feedback, Logger logger) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.firstOpen = Objects.requireNonNull(firstOpen, "firstOpen");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean accepting() { return accepting; }

    public void stopAccepting() { accepting = false; }

    public int inFlightCount() { return firstOpen.inFlightCount(); }

    public int pendingAttemptCount() { return attempts.size(); }

    public void openBlock(Player player, Location blockLocation, ContainerDescriptor descriptor) {
        Location origin = blockLocation.clone().add(0.5, 0.5, 0.5);
        beginOpen(player, descriptor, () -> origin.clone(),
                (task, retired) -> scheduler.executeAt(blockLocation, task),
                () -> resolver.resolve(blockLocation.getBlock()));
    }

    public void openEntity(Player player, Entity entity, ContainerDescriptor descriptor) {
        beginOpen(player, descriptor, () -> entity.getLocation().clone(),
                (task, retired) -> scheduler.executeFor(entity, task, retired),
                () -> resolver.resolve(entity));
    }

    private void beginOpen(Player player, ContainerDescriptor descriptor, Supplier<Location> originSupplier,
                           ContainerTaskDispatcher owner, Supplier<ContainerResolution> liveResolver) {
        UUID playerId = player.getUniqueId();
        OpenAttemptId attempt = OpenAttemptId.create();
        attempts.put(playerId, attempt);
        PlayerFeedback.LoadingToken loading = feedback.beginLoading(player);
        InstanceKey key = new InstanceKey(descriptor.containerId().orElseThrow(), playerId);
        var luckAttribute = player.getAttribute(Attribute.LUCK);
        float luck = luckAttribute == null ? 0.0F : (float) luckAttribute.getValue();
        CompletableFuture<EstablishedInstance> established = cache.get(key)
                .map(state -> CompletableFuture.completedFuture(
                        new EstablishedInstance(state.latestRecord(), false)))
                .orElseGet(() -> firstOpen.establish(key,
                        ignored -> generateOnOwner(descriptor, playerId, luck,
                                originSupplier, owner, liveResolver)));
        established.whenComplete((result, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                boolean cancelled = cause instanceof LootGenerationCancelledException;
                boolean unavailable = cause instanceof ContainerUnavailableException;
                fail(player, attempt, loading, cancelled,
                        cancelled || unavailable ? null : cause);
                return;
            }
            Runnable revalidate = () -> {
                try {
                    ContainerResolution live = liveResolver.get();
                    if (!(live instanceof ContainerResolution.Managed managed)
                            || !sameIdentity(descriptor, managed.descriptor())) {
                        fail(player, attempt, loading, false, null);
                        return;
                    }
                    LootInstanceState state = cache.establish(result.record());
                    scheduler.executeFor(player,
                            () -> openIfCurrent(player, attempt, loading, descriptor, state, result.created()),
                            () -> {
                                attempts.remove(playerId, attempt);
                                feedback.discard(loading);
                            });
                } catch (Throwable revalidationFailure) {
                    fail(player, attempt, loading, false, revalidationFailure);
                }
            };
            try {
                owner.execute(revalidate, () -> ownerRetired(player, attempt, loading));
            } catch (Throwable dispatchFailure) {
                fail(player, attempt, loading, false, dispatchFailure);
            }
        });
    }

    private CompletableFuture<LootInstanceRecord> generateOnOwner(
            ContainerDescriptor expected, UUID playerId, float luck, Supplier<Location> originSupplier,
            ContainerTaskDispatcher owner, Supplier<ContainerResolution> liveResolver
    ) {
        return OwnerTaskFuture.supply(owner, () -> {
            ContainerResolution live = liveResolver.get();
            if (!(live instanceof ContainerResolution.Managed managed)
                    || !sameIdentity(expected, managed.descriptor())) {
                throw new ContainerUnavailableException("container changed before loot generation");
            }
            // Entity locations are captured here, after revalidation, on the entity's owner.
            Location liveOrigin = Objects.requireNonNull(originSupplier.get(), "origin supplier returned null");
            return generator.generate(managed.descriptor(), liveOrigin, playerId, luck,
                    ThreadLocalRandom.current().nextLong());
        }, () -> new ContainerUnavailableException("container retired before loot generation"));
    }

    private void openIfCurrent(Player player, OpenAttemptId attempt, PlayerFeedback.LoadingToken loading,
                               ContainerDescriptor descriptor, LootInstanceState state, boolean created) {
        if (!player.isOnline() || !attempt.equals(attempts.get(player.getUniqueId()))) {
            feedback.discard(loading);
            return;
        }
        try {
            LootInstanceRecord record = state.latestRecord();
            if (record.containerSize() != descriptor.logicalSize()) {
                throw new CodecException("Stored size does not match physical logical size");
            }
            var contents = codec.decode(record.inventoryData(), descriptor.logicalSize(), record.codecVersion());
            if (!attempts.remove(player.getUniqueId(), attempt)) {
                feedback.discard(loading);
                return;
            }
            if (sessions.open(player, attempt, state, contents, title(descriptor), descriptor, created)) {
                feedback.opened(player, loading, created);
            } else {
                feedback.unavailable(player, loading);
            }
        } catch (CodecException failure) {
            state.corrupt(failure.getMessage());
            logger.error("Refusing to open undecodable personal inventory {}", state.key(), failure);
            attempts.remove(player.getUniqueId(), attempt);
            feedback.unavailable(player, loading);
        }
    }

    private void fail(Player player, OpenAttemptId attempt, PlayerFeedback.LoadingToken loading,
                      boolean generationCancelled, Throwable failure) {
        scheduler.executeFor(player, () -> {
            if (attempts.remove(player.getUniqueId(), attempt)) {
                if (generationCancelled) {
                    feedback.generationCancelled(player, loading);
                } else {
                    feedback.unavailable(player, loading);
                }
            } else {
                feedback.discard(loading);
            }
        }, () -> {
            attempts.remove(player.getUniqueId(), attempt);
            feedback.discard(loading);
        });
        if (failure != null) {
            logger.error("Open attempt {} failed for {}", attempt.value(), player.getUniqueId(), failure);
        }
    }

    private void ownerRetired(Player player, OpenAttemptId attempt, PlayerFeedback.LoadingToken loading) {
        if (!attempts.remove(player.getUniqueId(), attempt)) {
            feedback.discard(loading);
            return;
        }
        scheduler.executeFor(player,
                () -> feedback.unavailable(player, loading),
                () -> feedback.discard(loading));
    }

    private static Component title(ContainerDescriptor descriptor) {
        return Component.text(switch (descriptor.kind()) {
            case BARREL -> "Loot Barrel";
            case STORAGE_MINECART -> "Loot Minecart";
            default -> "Loot Chest";
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
}
