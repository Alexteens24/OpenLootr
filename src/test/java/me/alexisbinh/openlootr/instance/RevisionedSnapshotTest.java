package me.alexisbinh.openlootr.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionedSnapshotTest {
    @Test
    void commitOnlyCleansTheExactLatestRevision() {
        RevisionedSnapshot instance = new RevisionedSnapshot(new byte[]{0}, 15);
        assertEquals(16, instance.replace(new byte[]{16}));
        RevisionedSnapshot.Snapshot writing = instance.snapshot();
        assertEquals(17, instance.replace(new byte[]{17}));

        instance.markCommitted(writing.revision());
        assertTrue(instance.dirty());
        assertEquals(16, instance.committedRevision());
        assertEquals(17, instance.currentRevision());

        instance.markCommitted(17);
        assertFalse(instance.dirty());
    }
}
