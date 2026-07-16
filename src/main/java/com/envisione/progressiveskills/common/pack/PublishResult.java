package com.envisione.progressiveskills.common.pack;

import java.util.Objects;
import java.util.Optional;

/** Atomic staged-publication result. */
public record PublishResult(boolean published, String message, Optional<LivePackState> liveState) {
    public PublishResult {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(liveState, "liveState");
        if (published != liveState.isPresent()) {
            throw new IllegalArgumentException("Only successful publication exposes the new live state");
        }
    }
}
