package me.alexisbinh.openlootr.paper.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe token and cooldown state, deliberately independent of Bukkit for regression testing. */
final class FeedbackState {
    enum Finish { STALE, HIDDEN, SHOWN }

    private final AtomicLong sequences = new AtomicLong();
    private final Map<UUID, LoadingEntry> loading = new ConcurrentHashMap<>();
    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();

    Begin begin(UUID playerId) {
        long sequence = sequences.incrementAndGet();
        LoadingEntry previous = loading.put(playerId, new LoadingEntry(sequence));
        return new Begin(new PlayerFeedback.LoadingToken(playerId, sequence),
                previous != null && previous.finish() == Finish.SHOWN);
    }

    boolean markShown(PlayerFeedback.LoadingToken token) {
        LoadingEntry expected = loading.get(token.playerId());
        return expected != null && expected.sequence == token.sequence() && expected.markShown();
    }

    Finish finish(PlayerFeedback.LoadingToken token) {
        LoadingEntry expected = loading.get(token.playerId());
        if (expected == null || expected.sequence != token.sequence()) {
            return Finish.STALE;
        }
        Finish finish = expected.finish();
        if (finish == Finish.STALE) {
            return finish;
        }
        loading.remove(token.playerId(), expected);
        return finish;
    }

    boolean acquire(UUID playerId, FeedbackKind kind, long nowNanos, long cooldownNanos) {
        CooldownKey key = new CooldownKey(playerId, kind);
        boolean[] acquired = {false};
        cooldowns.compute(key, (ignored, previous) -> {
            if (previous == null || nowNanos - previous >= cooldownNanos) {
                acquired[0] = true;
                return nowNanos;
            }
            return previous;
        });
        return acquired[0];
    }

    enum FeedbackKind { UNAVAILABLE, CANNOT_BREAK, CANNOT_MERGE, CONTAINER_DISAPPEARED }

    record Begin(PlayerFeedback.LoadingToken token, boolean supersededShown) { }

    private static final class LoadingEntry {
        private final long sequence;
        private boolean shown;
        private boolean finished;

        private LoadingEntry(long sequence) { this.sequence = sequence; }

        private synchronized boolean markShown() {
            if (shown || finished) {
                return false;
            }
            shown = true;
            return true;
        }

        private synchronized Finish finish() {
            if (finished) {
                return Finish.STALE;
            }
            finished = true;
            return shown ? Finish.SHOWN : Finish.HIDDEN;
        }
    }

    private record CooldownKey(UUID playerId, FeedbackKind kind) { }
}
