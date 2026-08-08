package me.alexisbinh.openlootr.storage;

import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteLootStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void initializesDurableSchemaIdempotently() {
        Path database = tempDirectory.resolve("openlootr.db");
        SqliteLootStorage first = new SqliteLootStorage(database);
        first.initialize();
        assertTrue(first.health().initialized());
        assertEquals(SqliteLootStorage.SCHEMA_VERSION, first.health().schemaVersion());
        assertEquals("wal", first.health().journalMode());
        assertEquals("FULL", first.health().synchronousMode());
        first.close();

        SqliteLootStorage reopened = new SqliteLootStorage(database);
        reopened.initialize();
        assertEquals(SqliteLootStorage.SCHEMA_VERSION, reopened.health().schemaVersion());
        reopened.close();
    }

    @Test
    void primaryKeyAndCasPreventOlderOverwrite() {
        SqliteLootStorage storage = new SqliteLootStorage(tempDirectory.resolve("cas.db"));
        storage.initialize();
        InstanceKey key = new InstanceKey(UUID.randomUUID(), UUID.randomUUID());
        LootInstanceRecord revision0 = record(key, 0, new byte[]{0});
        assertTrue(storage.insertFirst(revision0));
        assertFalse(storage.insertFirst(revision0));
        assertEquals(1, storage.countByContainer(key.containerId()));
        assertEquals(0, storage.countByContainer(UUID.randomUUID()));

        LootInstanceRecord revision17 = new LootInstanceRecord(
                key, 27, 1, 17, 999L, new byte[]{17}, 1_700_000_000_000L, 1_700_000_000_017L);
        assertTrue(storage.updateCas(revision17, 0));
        LootInstanceRecord stale15 = record(key, 15, new byte[]{15});
        assertFalse(storage.updateCas(stale15, 0));

        LootInstanceRecord loaded = storage.find(key).orElseThrow();
        assertEquals(17, loaded.revision());
        assertEquals(42L, loaded.generationSeed(), "generation seed is immutable after first insert");
        assertArrayEquals(new byte[]{17}, loaded.inventoryData());
        storage.close();
    }

    private static LootInstanceRecord record(InstanceKey key, long revision, byte[] bytes) {
        long now = 1_700_000_000_000L + revision;
        return new LootInstanceRecord(key, 27, 1, revision, 42L, bytes, now, now);
    }
}
