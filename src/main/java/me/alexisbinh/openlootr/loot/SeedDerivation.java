package me.alexisbinh.openlootr.loot;

public final class SeedDerivation {
    private SeedDerivation() { }

    /** SplitMix64 output used to give each physical source a stable independent stream. */
    public static long sourceSeed(long personalSeed, int sourceIndex) {
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("source index must not be negative");
        }
        long value = personalSeed + 0x9E3779B97F4A7C15L * (sourceIndex + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
