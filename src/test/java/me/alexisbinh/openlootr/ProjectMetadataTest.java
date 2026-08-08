package me.alexisbinh.openlootr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMetadataTest {
    @Test
    void productionMetadataPinsPaperAndFoliaContract() throws IOException {
        String metadata = Files.readString(Path.of("src/main/resources/paper-plugin.yml"));
        assertTrue(metadata.contains("api-version: '1.21.11'"));
        assertTrue(metadata.contains("folia-supported: true"));
        assertTrue(metadata.contains("bootstrapper: me.alexisbinh.openlootr.OpenLootrBootstrap"));
    }

    @Test
    void spikePluginIsNotDeclaredByProductionMetadata() throws IOException {
        String metadata = Files.readString(Path.of("src/main/resources/paper-plugin.yml"));
        assertTrue(!metadata.contains("OpenLootrSpikes"));
    }
}
