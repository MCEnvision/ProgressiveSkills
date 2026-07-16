package com.envisione.progressiveskills.common.pack;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Cached exact dry-run candidate reviewed before publish. */
public record StageAttempt(StagingResult result, Optional<SemanticDiff> diff, Instant stagedAt) {
    public StageAttempt {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(diff, "diff");
        Objects.requireNonNull(stagedAt, "stagedAt");
        if (result.valid() != diff.isPresent()) {
            throw new IllegalArgumentException("Only a valid staged snapshot has a semantic diff");
        }
    }
}
