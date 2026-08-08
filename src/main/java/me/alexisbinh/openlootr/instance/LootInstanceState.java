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
    private long lastAccessAt = System.currentTimeMillis();
    private String faultReason;
    private PersistenceHealth persistenceHealth = PersistenceHealth.HEALTHY;

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
        touch();
        return record(new RevisionedSnapshot.Snapshot(revision, encoded));
    }

    public synchronized LootInstanceRecord latestRecord() {
        return record(revisions.snapshot());
    }

    public synchronized long committedRevision() { return revisions.committedRevision(); }
    public synchronized long currentRevision() { return revisions.currentRevision(); }
    public synchronized boolean dirty() { return revisions.dirty(); }
    public synchronized boolean degraded() { return persistenceHealth != PersistenceHealth.HEALTHY; }
    public synchronized PersistenceHealth persistenceHealth() { return persistenceHealth; }
    public synchronized int openSessions() { return openSessions; }
    public synchronized long lastAccessAt() { return lastAccessAt; }
    public synchronized String faultReason() { return faultReason; }
    public InstanceKey key() { return key; }
    public int containerSize() { return containerSize; }
    public int codecVersion() { return codecVersion; }

    public synchronized void touch() { lastAccessAt = System.currentTimeMillis(); }
    public synchronized void opened() { openSessions++; touch(); }
    public synchronized void closed() { openSessions = Math.max(0, openSessions - 1); touch(); }

    public synchronized void corrupt(String reason) {
        persistenceHealth = PersistenceHealth.CORRUPT;
        faultReason = Objects.requireNonNull(reason, "reason");
        touch();
    }

    public synchronized boolean committed(long revision) {
        revisions.markCommitted(revision);
        consecutiveFailures = 0;
        firstFailureAt = 0;
        if (persistenceHealth == PersistenceHealth.DEGRADED) {
            persistenceHealth = PersistenceHealth.HEALTHY;
            faultReason = null;
            return true;
        }
        return false;
    }

    public synchronized boolean failed() {
        long now = System.currentTimeMillis();
        if (firstFailureAt == 0) {
            firstFailureAt = now;
        }
        consecutiveFailures++;
        return consecutiveFailures >= 5 || now - firstFailureAt >= 30_000L;
    }

    public synchronized int consecutiveFailures() { return consecutiveFailures; }

    public synchronized boolean beginDegrading(PersistenceHealth target) {
        if (target != PersistenceHealth.DEGRADED && target != PersistenceHealth.QUARANTINED) {
            throw new IllegalArgumentException("degraded target must be DEGRADED or QUARANTINED");
        }
        boolean initialFailure = persistenceHealth == PersistenceHealth.HEALTHY;
        boolean quarantineEscalation = persistenceHealth == PersistenceHealth.DEGRADED
                && target == PersistenceHealth.QUARANTINED;
        if (!initialFailure && !quarantineEscalation) {
            return false;
        }
        persistenceHealth = PersistenceHealth.DEGRADING;
        return true;
    }

    public synchronized void finishDegrading(PersistenceHealth target) {
        if (target != PersistenceHealth.DEGRADED && target != PersistenceHealth.QUARANTINED) {
            throw new IllegalArgumentException("degraded target must be DEGRADED or QUARANTINED");
        }
        persistenceHealth = target;
        faultReason = target == PersistenceHealth.QUARANTINED
                ? "database revision conflict" : "database writes repeatedly failed";
    }

    private LootInstanceRecord record(RevisionedSnapshot.Snapshot snapshot) {
        return new LootInstanceRecord(key, containerSize, codecVersion, snapshot.revision(), generationSeed,
                snapshot.data(), createdAt, Instant.now().toEpochMilli());
    }
}
