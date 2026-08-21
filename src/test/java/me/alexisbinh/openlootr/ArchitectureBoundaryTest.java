package me.alexisbinh.openlootr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {
    @Test
    void domainAndStorageDoNotImportPaperWorldTypes() throws IOException {
        Path sourceRoot = Path.of("src/main/java/me/alexisbinh/openlootr");
        List<String> restricted = List.of("container", "instance", "session", "storage", "runtime");
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Path relative = sourceRoot.relativize(file);
                if (relative.getNameCount() == 0 || !restricted.contains(relative.getName(0).toString())) {
                    continue;
                }
                String source = Files.readString(file);
                assertFalse(source.contains("import org.bukkit"), () -> relative + " imports Bukkit");
                assertFalse(source.contains("import io.papermc"), () -> relative + " imports Paper");
            }
        }
    }

    @Test
    void minecraftInternalsAreIsolatedToLinkageLayout() throws IOException {
        Path sourceRoot = Path.of("src/main/java/me/alexisbinh/openlootr");
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("import net.minecraft"),
                        () -> sourceRoot.relativize(file) + " directly imports NMS");
                if (source.contains("\"net.minecraft.")
                        || source.contains("\"org.bukkit.craftbukkit.")) {
                    Path relative = sourceRoot.relativize(file);
                    assertTrue(relative.toString().equals("paper/nms/LinkageLayout.java"),
                            () -> relative + " names internals outside the linkage layout");
                }
            }
        }
    }

    @Test
    void nmsCompatibilityHasNoVersionedSourceDirectories() throws IOException {
        Path nmsRoot = Path.of("src/main/java/me/alexisbinh/openlootr/paper/nms");
        try (var entries = Files.list(nmsRoot)) {
            assertFalse(entries.anyMatch(Files::isDirectory),
                    "NMS compatibility must remain one flat source island");
        }
    }
}
