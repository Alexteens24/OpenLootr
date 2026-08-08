package me.alexisbinh.openlootr.paper.feedback;

import org.bukkit.entity.Player;

import java.util.UUID;

/** Player-facing feedback boundary. Technical diagnostics stay in logs and inspector tooling. */
public interface PlayerFeedback {
    PlayerFeedback NOOP = new PlayerFeedback() {
        @Override public LoadingToken beginLoading(Player player) {
            return new LoadingToken(player.getUniqueId(), 0L);
        }

        @Override public void opened(Player player, LoadingToken token, boolean firstOpen) { }
        @Override public void unavailable(Player player, LoadingToken token) { }
        @Override public void generationCancelled(Player player, LoadingToken token) { }
        @Override public void discard(LoadingToken token) { }
        @Override public void unavailable(Player player) { }
        @Override public void cannotBreak(Player player) { }
        @Override public void cannotMerge(Player player) { }
        @Override public void containerDisappeared(Player player) { }
    };

    LoadingToken beginLoading(Player player);

    void opened(Player player, LoadingToken token, boolean firstOpen);

    void unavailable(Player player, LoadingToken token);

    void generationCancelled(Player player, LoadingToken token);

    /** Retires a token without touching a Bukkit object or clearing newer feedback. */
    void discard(LoadingToken token);

    void unavailable(Player player);

    void cannotBreak(Player player);

    void cannotMerge(Player player);

    void containerDisappeared(Player player);

    record LoadingToken(UUID playerId, long sequence) { }
}
