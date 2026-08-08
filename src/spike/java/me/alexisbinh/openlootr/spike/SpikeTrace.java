package me.alexisbinh.openlootr.spike;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

final class SpikeTrace {
    private final Plugin plugin;
    private final Path output;

    SpikeTrace(Plugin plugin) {
        this.plugin = plugin;
        this.output = plugin.getDataFolder().toPath().resolve("spike-results.jsonl");
    }

    synchronized void record(String probe, String event, Map<String, ?> fields) {
        try {
            Files.createDirectories(output.getParent());
            String extras = fields.entrySet().stream()
                    .map(entry -> "\"" + escape(entry.getKey()) + "\":\""
                            + escape(String.valueOf(entry.getValue())) + "\"")
                    .collect(Collectors.joining(","));
            String line = "{\"at\":\"" + Instant.now() + "\",\"probe\":\""
                    + escape(probe) + "\",\"event\":\"" + escape(event)
                    + "\",\"thread\":\"" + escape(Thread.currentThread().getName()) + "\""
                    + (extras.isEmpty() ? "" : "," + extras) + "}\n";
            Files.writeString(output, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getSLF4JLogger().error("Failed to append spike trace", exception);
        }
    }

    Path output() {
        return output;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
