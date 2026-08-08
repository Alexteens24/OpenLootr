package me.alexisbinh.openlootr.paper.feedback;

import me.alexisbinh.openlootr.scheduler.SchedulerFacade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** MiniMessage actionbar feedback with delayed loading and per-player spam protection. */
public final class PlayerFeedbackService implements PlayerFeedback {
    private static final Duration LOADING_DELAY = Duration.ofMillis(150);
    private static final long COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);

    private final SchedulerFacade scheduler;
    private final FeedbackState state = new FeedbackState();
    private final Map<Message, Component> messages;
    private final LongSupplier nanoTime;

    public PlayerFeedbackService(JavaPlugin plugin, SchedulerFacade scheduler) {
        this(scheduler, loadMessages(plugin), System::nanoTime);
    }

    PlayerFeedbackService(SchedulerFacade scheduler, Map<Message, Component> messages,
                          LongSupplier nanoTime) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Map.copyOf(messages);
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override
    public LoadingToken beginLoading(Player player) {
        FeedbackState.Begin begin = state.begin(player.getUniqueId());
        LoadingToken token = begin.token();
        if (begin.supersededShown()) {
            player.sendActionBar(Component.empty());
        }
        scheduler.executeAsyncLater(() -> scheduler.executeFor(player, () -> {
            if (player.isOnline() && state.markShown(token)) {
                player.sendActionBar(message(Message.LOADING));
            }
        }, () -> state.finish(token)), LOADING_DELAY);
        return token;
    }

    @Override
    public void opened(Player player, LoadingToken token, boolean firstOpen) {
        FeedbackState.Finish finish = state.finish(token);
        if (finish == FeedbackState.Finish.STALE) {
            return;
        }
        if (!firstOpen) {
            clearIfShown(player, finish);
            return;
        }
        player.sendActionBar(message(Message.LOOT_GENERATED));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25F, 1.4F);
        player.spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0.0, 1.0, 0.0), 2, 0.15, 0.15, 0.15, 0.0);
    }

    @Override
    public void unavailable(Player player, LoadingToken token) {
        FeedbackState.Finish finish = state.finish(token);
        if (finish != FeedbackState.Finish.STALE) {
            boolean sent = sendCooled(player, FeedbackState.FeedbackKind.UNAVAILABLE, Message.UNAVAILABLE);
            if (!sent) {
                clearIfShown(player, finish);
            }
        }
    }

    @Override
    public void generationCancelled(Player player, LoadingToken token) {
        FeedbackState.Finish finish = state.finish(token);
        if (finish != FeedbackState.Finish.STALE) {
            player.sendActionBar(message(Message.GENERATION_CANCELLED));
        }
    }

    @Override
    public void discard(LoadingToken token) {
        state.finish(token);
    }

    @Override
    public void unavailable(Player player) {
        sendCooled(player, FeedbackState.FeedbackKind.UNAVAILABLE, Message.UNAVAILABLE);
    }

    @Override
    public void cannotBreak(Player player) {
        sendCooled(player, FeedbackState.FeedbackKind.CANNOT_BREAK, Message.CANNOT_BREAK);
    }

    @Override
    public void cannotMerge(Player player) {
        sendCooled(player, FeedbackState.FeedbackKind.CANNOT_MERGE, Message.CANNOT_MERGE);
    }

    @Override
    public void containerDisappeared(Player player) {
        sendCooled(player, FeedbackState.FeedbackKind.CONTAINER_DISAPPEARED,
                Message.CONTAINER_DISAPPEARED);
    }

    private void clearIfShown(Player player, FeedbackState.Finish finish) {
        if (finish == FeedbackState.Finish.SHOWN) {
            player.sendActionBar(Component.empty());
        }
    }

    private boolean sendCooled(Player player, FeedbackState.FeedbackKind kind, Message message) {
        if (state.acquire(player.getUniqueId(), kind, nanoTime.getAsLong(), COOLDOWN_NANOS)) {
            player.sendActionBar(message(message));
            return true;
        }
        return false;
    }

    private Component message(Message key) {
        return messages.getOrDefault(key, Component.empty());
    }

    private static Map<Message, Component> loadMessages(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.saveResource("messages.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "messages.yml"));
        MiniMessage miniMessage = MiniMessage.miniMessage();
        Map<Message, Component> loaded = new EnumMap<>(Message.class);
        for (Message message : Message.values()) {
            loaded.put(message, miniMessage.deserialize(yaml.getString(message.key, message.fallback)));
        }
        return loaded;
    }

    enum Message {
        CANNOT_BREAK("cannot-break", "<red>You cannot break this loot container."),
        CANNOT_MERGE("cannot-merge", "<red>This protected chest cannot be merged."),
        LOADING("loading", "<gray>Loading your personal loot..."),
        UNAVAILABLE("unavailable", "<red>This loot container is temporarily unavailable."),
        GENERATION_CANCELLED("generation-cancelled", "<yellow>Loot generation was cancelled."),
        LOOT_GENERATED("loot-generated", "<green>Loot generated for you!"),
        CONTAINER_DISAPPEARED("container-disappeared", "<red>This loot container is no longer available.");

        private final String key;
        private final String fallback;

        Message(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }
}
