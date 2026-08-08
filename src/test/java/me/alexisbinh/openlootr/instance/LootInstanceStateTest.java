package me.alexisbinh.openlootr.instance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootInstanceStateTest {
    @Test
    void olderCommitCannotCleanNewerMutation() {
        InstanceKey key = new InstanceKey(UUID.randomUUID(), UUID.randomUUID());
        LootInstanceState state = new LootInstanceState(new LootInstanceRecord(
                key, 27, 1, 15, 7L, new byte[]{15}, 1L, 1L));

        LootInstanceRecord revision16 = state.replace(new byte[]{16});
        LootInstanceRecord revision17 = state.replace(new byte[]{17});
        state.committed(revision16.revision());

        assertTrue(state.dirty());
        assertEquals(16, state.committedRevision());
        assertEquals(17, revision17.revision());
        state.committed(revision17.revision());
        assertFalse(state.dirty());
    }

    @Test
    void fifthConsecutiveFailureEntersDegradedMode() {
        InstanceKey key = new InstanceKey(UUID.randomUUID(), UUID.randomUUID());
        LootInstanceState state = new LootInstanceState(new LootInstanceRecord(
                key, 27, 1, 0, 7L, new byte[]{0}, 1L, 1L));
        state.replace(new byte[]{1});

        for (int attempt = 1; attempt < 5; attempt++) {
            assertFalse(state.failed());
        }
        assertTrue(state.failed());
        assertTrue(state.degraded());
    }
}
