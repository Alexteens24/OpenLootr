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
        assertTrue(state.beginDegrading(PersistenceHealth.DEGRADED));
        state.finishDegrading(PersistenceHealth.DEGRADED);
        assertTrue(state.degraded());
    }

    @Test
    void transientDegradedStateRecoversButQuarantineDoesNot() {
        InstanceKey key = new InstanceKey(UUID.randomUUID(), UUID.randomUUID());
        LootInstanceState recoverable = new LootInstanceState(new LootInstanceRecord(
                key, 27, 1, 0, 7L, new byte[]{0}, 1L, 1L));
        LootInstanceRecord revision1 = recoverable.replace(new byte[]{1});
        assertTrue(recoverable.beginDegrading(PersistenceHealth.DEGRADED));
        recoverable.finishDegrading(PersistenceHealth.DEGRADED);
        assertTrue(recoverable.committed(revision1.revision()));
        assertEquals(PersistenceHealth.HEALTHY, recoverable.persistenceHealth());

        LootInstanceState quarantined = new LootInstanceState(new LootInstanceRecord(
                new InstanceKey(UUID.randomUUID(), UUID.randomUUID()),
                27, 1, 0, 7L, new byte[]{0}, 1L, 1L));
        LootInstanceRecord quarantinedRevision = quarantined.replace(new byte[]{1});
        assertTrue(quarantined.beginDegrading(PersistenceHealth.QUARANTINED));
        quarantined.finishDegrading(PersistenceHealth.QUARANTINED);
        assertFalse(quarantined.committed(quarantinedRevision.revision()));
        assertEquals(PersistenceHealth.QUARANTINED, quarantined.persistenceHealth());

        LootInstanceState escalation = new LootInstanceState(new LootInstanceRecord(
                new InstanceKey(UUID.randomUUID(), UUID.randomUUID()),
                27, 1, 0, 7L, new byte[]{0}, 1L, 1L));
        assertTrue(escalation.beginDegrading(PersistenceHealth.DEGRADED));
        escalation.finishDegrading(PersistenceHealth.DEGRADED);
        assertTrue(escalation.beginDegrading(PersistenceHealth.QUARANTINED));
        escalation.finishDegrading(PersistenceHealth.QUARANTINED);
        assertEquals(PersistenceHealth.QUARANTINED, escalation.persistenceHealth());
    }
}
