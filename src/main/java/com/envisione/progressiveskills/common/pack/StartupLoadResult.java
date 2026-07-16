package com.envisione.progressiveskills.common.pack;

import java.util.Objects;
import java.util.Optional;

/** Startup publication or safe-disabled outcome. */
public record StartupLoadResult(Optional<LivePackState> live, StagingResult primaryResult, boolean recovered) {
    public StartupLoadResult {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(primaryResult, "primaryResult");
        if (recovered && live.isEmpty()) {
            throw new IllegalArgumentException("Recovered startup requires a live state");
        }
    }
}
