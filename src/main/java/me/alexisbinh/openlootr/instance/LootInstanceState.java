package me.alexisbinh.openlootr.instance;

import java.time.Instant;
import java.util.Objects;

/** Thread-safe persistence state. Item serialization is performed before data enters this object. */
public final class LootInstanceState {
    private final InstanceKey key;
    private final int containerSize;
    private final int codecVersion;
    private final long generationSeed;
    private final long createdAt;
    private final RevisionedSnapshot revisions;
    private int openSessions;
    private int consecutiveFailures;
    private long firstFailureAt;
    private boolean degraded;

    public LootInstanceState(LootInstanceRecord record) {
        Objects.requireNonNull(record, "record");
        key = record.key();
        containerSize = record.containerSize();
        codecVersion = record.codecVersion();
        generationSeed = record.generationSeed();
        createdAt = record.createdAt();
        revisions = new RevisionedSnapshot(record.inventoryData(), record.revision());
    }

    public synchronized LootInstanceRecord replace(byte[] encoded) {
        long revision = revisions.replace(encoded);
        return record(new RevisionedSnapshot.Snapshot(revision, encoded));
    }

    public synchronized LootInstanceRecord latestRecord() {
        return record(revisions.snapshot());
    }

    public synchronized long committedRevision() { return revisions.committedRevision(); }
    public synchronized boolean dirty() { return revisions.dirty(); }
    public synchronized boolean degraded() { return degraded; }
    public synchronized int openSessions() { return openSessions; }
    public InstanceKey key() { return key; }
    public int containerSize() { return containerSize; }
    public int codecVersion() { return codecVersion; }

    public synchronized void opened() { openSessions++; }
    public synchronized void closed() { openSessions = Math.max(0, openSessions - 1); }

    public synchronized void committed(long revision) {
        revisions.markCommitted(revision);
        consecutiveFailures = 0;
        firstFailureAt = 0;
    }

    public synchronized boolean failed() {
        long now = System.currentTimeMillis();
        if (firstFailureAt == 0) {
            firstFailureAt = now;
        }
        consecutiveFailures++;
        degraded = consecutiveFailures >= 5 || now - firstFailureAt >= 30_000L;
        return degraded;
    }

    public synchronized int consecutiveFailures() { return consecutiveFailures; }

    public synchronized void markDegraded() { degraded = true; }

    private LootInstanceRecord record(RevisionedSnapshot.Snapshot snapshot) {
        return new LootInstanceRecord(key, containerSize, codecVersion, snapshot.revision(), generationSeed,
                snapshot.data(), createdAt, Instant.now().toEpochMilli());
    }
}
