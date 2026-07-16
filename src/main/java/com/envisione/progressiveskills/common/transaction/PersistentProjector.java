package com.envisione.progressiveskills.common.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Physical adapter for one atomic batch of source-resolved persistent changes. */
public interface PersistentProjector {
    /** Returns a rejection reason before any state mutation, or empty when the batch is supported. */
    Optional<String> validate(UUID targetId, List<ProjectionChange> changes);

    /** Applies the prevalidated batch atomically or throws before retaining any partial projection. */
    void apply(UUID targetId, List<ProjectionChange> changes);
}
