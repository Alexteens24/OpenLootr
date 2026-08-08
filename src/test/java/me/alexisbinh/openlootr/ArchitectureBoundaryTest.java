package me.alexisbinh.openlootr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
