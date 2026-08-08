package me.alexisbinh.openlootr.storage;

import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;

import java.util.Optional;

public interface LootStorage extends AutoCloseable {
    void initialize();

    Optional<LootInstanceRecord> find(InstanceKey key);

    boolean insertFirst(LootInstanceRecord record);

    boolean updateCas(LootInstanceRecord record, long expectedRevision);

    StorageHealth health();

    @Override
    void close();
}
