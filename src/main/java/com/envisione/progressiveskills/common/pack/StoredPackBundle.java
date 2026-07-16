package com.envisione.progressiveskills.common.pack;

import java.time.Instant;
import java.util.Objects;

/** Verified last-known-good source bundle and its publication metadata. */
public record StoredPackBundle(
        long generation,
        String contentDigest,
        String bundleDigest,
        Instant createdAt,
        PackLockMetadata lockMetadata,
        SourceBundle sourceBundle
) {
    public StoredPackBundle {
        if (generation < 1) {
            throw new IllegalArgumentException("Stored generation must be positive");
        }
        if (!contentDigest.matches("[0-9a-f]{64}") || !bundleDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Stored digests must be lowercase SHA-256");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lockMetadata, "lockMetadata");
        Objects.requireNonNull(sourceBundle, "sourceBundle");
    }
}
