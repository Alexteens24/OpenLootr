package me.alexisbinh.openlootr.instance;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceCacheTest {
    @Test
    void evictsOnlyIdleCleanHealthyClosedInstances() throws Exception {
        InstanceCache cache = new InstanceCache(Duration.ofMillis(2));
        LootInstanceState clean = cache.establish(record());
        Thread.sleep(5L);
        assertEquals(1, cache.evictIdle());

        LootInstanceState dirty = cache.establish(record());
        dirty.replace(new byte[]{2});
        Thread.sleep(5L);
        assertEquals(0, cache.evictIdle());

        dirty.committed(dirty.currentRevision());
        dirty.corrupt("fixture corruption");
        Thread.sleep(5L);
        assertEquals(0, cache.evictIdle());
    }

    private static LootInstanceRecord record() {
        return new LootInstanceRecord(new InstanceKey(UUID.randomUUID(), UUID.randomUUID()),
                27, 1, 0, 4L, new byte[]{1}, 1L, 1L);
    }
}
