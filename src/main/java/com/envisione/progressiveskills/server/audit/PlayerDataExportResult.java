package com.envisione.progressiveskills.server.audit;

import java.nio.file.Path;
import java.util.Objects;

/** Verified result from one bounded maintenance snapshot or readable export. */
public record PlayerDataExportResult(Path path, String playerDataDigest, long bytes) {
    public PlayerDataExportResult {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        playerDataDigest = Objects.requireNonNull(playerDataDigest, "playerDataDigest");
        if (!playerDataDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Export digest must be lowercase SHA-256");
        }
        if (bytes < 1) {
            throw new IllegalArgumentException("Export must contain at least one byte");
        }
    }
}
