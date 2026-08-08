package me.alexisbinh.openlootr.instance;

import java.util.Arrays;
import java.util.Objects;

public final class RevisionedSnapshot {
    private byte[] data;
    private long currentRevision;
    private long committedRevision;

    public RevisionedSnapshot(byte[] data, long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        this.data = copy(data);
        this.currentRevision = revision;
        this.committedRevision = revision;
    }

    public synchronized long replace(byte[] latestData) {
        data = copy(latestData);
        return ++currentRevision;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(currentRevision, copy(data));
    }

    public synchronized void markCommitted(long revision) {
        if (revision > currentRevision) {
            throw new IllegalArgumentException("cannot commit a future revision");
        }
        committedRevision = Math.max(committedRevision, revision);
    }

    public synchronized boolean dirty() {
        return currentRevision > committedRevision;
    }

    public synchronized long currentRevision() {
        return currentRevision;
    }

    public synchronized long committedRevision() {
        return committedRevision;
    }

    private static byte[] copy(byte[] value) {
        return Arrays.copyOf(Objects.requireNonNull(value, "data"), value.length);
    }

    public record Snapshot(long revision, byte[] data) {
        public Snapshot {
            data = copy(data);
        }

        @Override
        public byte[] data() {
            return copy(data);
        }
    }
}
