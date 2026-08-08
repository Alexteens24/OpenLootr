package me.alexisbinh.openlootr.paper.nms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedMinecraftVersionsTest {
    @Test
    void exposesOnlyTheExactRuntimeAllowlist() {
        assertEquals(List.of("1.21.11", "26.1.2", "26.2"),
                SupportedMinecraftVersions.values());
        assertTrue(SupportedMinecraftVersions.supports("1.21.11"));
        assertTrue(SupportedMinecraftVersions.supports("26.1.2"));
        assertTrue(SupportedMinecraftVersions.supports("26.2"));
        assertFalse(SupportedMinecraftVersions.supports("26.1"));
        assertFalse(SupportedMinecraftVersions.supports("26.3"));
    }

    @Test
    void unsupportedRuntimeFailsBeforeCompatibilityLinkage() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SupportedMinecraftVersions.requireSupported("26.3"));
        assertTrue(failure.getMessage().contains("1.21.11, 26.1.2, 26.2"));
    }
}
