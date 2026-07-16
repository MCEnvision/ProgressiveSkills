package com.envisione.progressiveskills.common.transaction;

import java.util.List;
import java.util.Objects;

/** Stable transaction result cached by idempotency key. */
public record TransactionResult(
        TransactionId transactionId,
        TransactionStatus status,
        long beforeRevision,
        long afterRevision,
        String diagnosticCode,
        String message,
        boolean replayed,
        List<ProjectionChange> projectionChanges,
        List<TransitionActionResult> actionResults
) {
    public TransactionResult {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(status, "status");
        if (beforeRevision < 0 || afterRevision < 0) {
            throw new IllegalArgumentException("Transaction revisions must not be negative");
        }
        diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnosticCode");
        message = Objects.requireNonNull(message, "message");
        projectionChanges = List.copyOf(Objects.requireNonNull(projectionChanges, "projectionChanges"));
        actionResults = List.copyOf(Objects.requireNonNull(actionResults, "actionResults"));
    }

    public TransactionResult asReplay() {
        if (replayed) {
            return this;
        }
        return new TransactionResult(
                transactionId,
                status,
                beforeRevision,
                afterRevision,
                diagnosticCode,
                message,
                true,
                projectionChanges,
                actionResults
        );
    }
}
