package com.envisione.progressiveskills.common.transaction;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded server-side audit entry for one terminal transaction. */
public record AuditRecord(
        TransactionId transactionId,
        UUID actorId,
        UUID targetId,
        ProgressionCause cause,
        String reason,
        DefinitionRevision definitionRevision,
        Instant completedAt,
        TransactionStatus status,
        long beforeRevision,
        long afterRevision,
        boolean reversible,
        List<BalanceMutation> balanceMutations,
        List<PaidCostMutation> paidCostMutations,
        List<ProjectionChange> projectionChanges,
        List<TransitionActionResult> actionResults,
        String message
) {
    public AuditRecord {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(cause, "cause");
        reason = Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(status, "status");
        balanceMutations = List.copyOf(Objects.requireNonNull(balanceMutations, "balanceMutations"));
        paidCostMutations = List.copyOf(Objects.requireNonNull(paidCostMutations, "paidCostMutations"));
        projectionChanges = List.copyOf(Objects.requireNonNull(projectionChanges, "projectionChanges"));
        actionResults = List.copyOf(Objects.requireNonNull(actionResults, "actionResults"));
        message = Objects.requireNonNull(message, "message");
    }

    public AuditRecord(
            TransactionId transactionId,
            UUID actorId,
            UUID targetId,
            ProgressionCause cause,
            String reason,
            DefinitionRevision definitionRevision,
            Instant completedAt,
            TransactionStatus status,
            long beforeRevision,
            long afterRevision,
            boolean reversible,
            List<BalanceMutation> balanceMutations,
            List<ProjectionChange> projectionChanges,
            List<TransitionActionResult> actionResults,
            String message
    ) {
        this(transactionId, actorId, targetId, cause, reason, definitionRevision, completedAt,
                status, beforeRevision, afterRevision, reversible, balanceMutations, List.of(),
                projectionChanges, actionResults, message);
    }
}
