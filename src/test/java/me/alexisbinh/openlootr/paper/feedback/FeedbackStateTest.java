package me.alexisbinh.openlootr.paper.feedback;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackStateTest {
    @Test
    void supersededLoadingTokenCannotShowOrClearNewAttempt() {
        FeedbackState state = new FeedbackState();
        UUID playerId = UUID.randomUUID();
        PlayerFeedback.LoadingToken old = state.begin(playerId).token();
        PlayerFeedback.LoadingToken current = state.begin(playerId).token();

        assertFalse(state.markShown(old));
        assertEquals(FeedbackState.Finish.STALE, state.finish(old));
        assertTrue(state.markShown(current));
        assertEquals(FeedbackState.Finish.SHOWN, state.finish(current));
    }

    @Test
    void fastCompletionNeverReportsLoadingAsShown() {
        FeedbackState state = new FeedbackState();
        PlayerFeedback.LoadingToken token = state.begin(UUID.randomUUID()).token();

        assertEquals(FeedbackState.Finish.HIDDEN, state.finish(token));
        assertFalse(state.markShown(token));
    }

    @Test
    void replacementReportsWhetherOldLoadingWasVisible() {
        FeedbackState state = new FeedbackState();
        UUID playerId = UUID.randomUUID();
        FeedbackState.Begin first = state.begin(playerId);
        assertTrue(state.markShown(first.token()));

        FeedbackState.Begin replacement = state.begin(playerId);

        assertTrue(replacement.supersededShown());
        assertFalse(state.markShown(first.token()));
    }

    @Test
    void cooldownIsScopedByPlayerAndFeedbackKind() {
        FeedbackState state = new FeedbackState();
        UUID playerId = UUID.randomUUID();

        assertTrue(state.acquire(playerId, FeedbackState.FeedbackKind.CANNOT_BREAK, 100L, 50L));
        assertFalse(state.acquire(playerId, FeedbackState.FeedbackKind.CANNOT_BREAK, 149L, 50L));
        assertTrue(state.acquire(playerId, FeedbackState.FeedbackKind.CANNOT_MERGE, 149L, 50L));
        assertTrue(state.acquire(playerId, FeedbackState.FeedbackKind.CANNOT_BREAK, 150L, 50L));
    }
}
