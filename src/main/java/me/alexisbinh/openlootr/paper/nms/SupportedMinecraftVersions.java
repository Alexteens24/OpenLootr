package me.alexisbinh.openlootr.paper.nms;

import java.util.List;
import java.util.Set;

/** Exact runtime allowlist. Compatibility behavior belongs to the linker, not this class. */
public final class SupportedMinecraftVersions {
    private static final List<String> ORDERED = List.of("1.21.11", "26.1.2", "26.2");
    private static final Set<String> SUPPORTED = Set.copyOf(ORDERED);

    private SupportedMinecraftVersions() { }

    public static void requireSupported(String version) {
        if (!SUPPORTED.contains(version)) {
            throw new IllegalStateException("Unsupported Minecraft version " + version
                    + "; supported versions: " + String.join(", ", ORDERED));
        }
    }

    public static boolean supports(String version) {
        return SUPPORTED.contains(version);
    }

    public static List<String> values() {
        return ORDERED;
    }
}
