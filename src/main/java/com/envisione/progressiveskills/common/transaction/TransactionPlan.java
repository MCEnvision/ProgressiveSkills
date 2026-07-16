package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;
import java.util.UUID;

/** Captured, revision-pinned root transaction plan. */
public record TransactionPlan(
        UUID actorId,
        UUID targetId,
        IdempotencyKey idempotencyKey,
        long expectedStateRevision,
        DefinitionRevision definitionRevision,
        ProgressionCause cause,
        String reason,
        TransactionStep rootStep
) {
    public static final int MAX_REASON_LENGTH = 256;

    public TransactionPlan {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (expectedStateRevision < 0) {
            throw new IllegalArgumentException("Expected state revision must not be negative");
        }
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        Objects.requireNonNull(cause, "cause");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH || reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Transaction reason must be bounded printable text");
        }
        Objects.requireNonNull(rootStep, "rootStep");
    }
}
