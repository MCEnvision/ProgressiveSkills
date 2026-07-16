package com.envisione.progressiveskills.common.pack;

import java.time.Instant;
import java.util.Objects;

/** One atomically published immutable definition generation. */
public record LivePackState(long generation, PackSnapshot snapshot, Instant publishedAt, boolean recovered) {
    public LivePackState {
        if (generation < 0) {
            throw new IllegalArgumentException("Live generation cannot be negative");
        }
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(publishedAt, "publishedAt");
    }

    public static LivePackState empty() {
        return new LivePackState(0, PackSnapshot.empty(), Instant.EPOCH, false);
    }
}
