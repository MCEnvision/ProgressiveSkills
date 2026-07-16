package com.envisione.progressiveskills.common.transaction;

import java.util.List;
import java.util.Objects;

/** Result of a persistent recompute or forced physical re-projection. */
public record ProjectionReport(boolean successful, String message, List<ProjectionChange> changes) {
    public ProjectionReport {
        message = Objects.requireNonNull(message, "message");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }
}
