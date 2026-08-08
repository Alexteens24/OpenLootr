package me.alexisbinh.openlootr.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SeedDerivationTest {
    @Test
    void matchesGoldenSplitMix64Vectors() {
        assertEquals(-2152535657050944081L, SeedDerivation.sourceSeed(0L, 0));
        assertEquals(7960286522194355700L, SeedDerivation.sourceSeed(0L, 1));
        assertEquals(-7995527694508729151L, SeedDerivation.sourceSeed(1L, 0));
        assertEquals(-4689498862643123097L, SeedDerivation.sourceSeed(1L, 1));
        assertEquals(1547611027431991965L, SeedDerivation.sourceSeed(0x0123456789ABCDEFL, 0));
        assertEquals(-3066016094752747373L, SeedDerivation.sourceSeed(0x0123456789ABCDEFL, 1));
    }

    @Test
    void sourcesAreIndependentAndRepeatable() {
        long personal = 42L;
        assertEquals(SeedDerivation.sourceSeed(personal, 0), SeedDerivation.sourceSeed(personal, 0));
        assertNotEquals(SeedDerivation.sourceSeed(personal, 0), SeedDerivation.sourceSeed(personal, 1));
    }
}
