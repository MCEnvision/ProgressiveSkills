package com.envisione.progressiveskills.server.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterPackInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installSeedsOnceWithoutOverwritingOperatorEdits() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(root);
        Path manifest = pack.resolve("pack.toml");
        assertTrue(Files.readString(manifest).contains("progressiveskills:core"));

        Files.writeString(manifest, "operator-owned");
        StarterPackInstaller.install(root);

        assertEquals("operator-owned", Files.readString(manifest));
    }

    @Test
    void installRejectsASymbolicStarterDirectoryBeforeWriting() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(root);
        Files.createDirectories(outside);
        Files.createSymbolicLink(root.resolve("progressiveskills-core"), outside);

        assertThrows(IOException.class, () -> StarterPackInstaller.install(root));
        try (var entries = Files.list(outside)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }
}
