package com.envisione.progressiveskills.common.data;

import java.util.Objects;

/** Quarantined state for a missing, conflicting, or lineage-incompatible definition. */
public record OrphanRecord(StoredDefinitionState state, String reason) {
    public static final int MAX_REASON_LENGTH = 512;

    public OrphanRecord {
        Objects.requireNonNull(state, "state");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Orphan reason must be bounded");
        }
    }
}
