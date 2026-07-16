package com.envisione.progressiveskills.server.offline;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Version-pinned checked balance mutation queued for an unloaded known player. */
public record PendingProgressionOperation(
        UUID operationId,
        UUID targetId,
        UUID issuerId,
        Instant createdAt,
        Instant expiresAt,
        DefinitionRevision definitionRevision,
        ResourceLocation balanceId,
        long delta,
        long minimum,
        long maximum,
        boolean rewardEligible,
        PendingOperationStatus status,
        Optional<UUID> lastAttemptId,
        Optional<String> quarantineReason
) implements Comparable<PendingProgressionOperation> {
    public static final int MAX_QUARANTINE_REASON = 512;

    public PendingProgressionOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(issuerId, "issuerId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Pending operation expiry must follow creation");
        }
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        Objects.requireNonNull(balanceId, "balanceId");
        if (delta == 0 || minimum > maximum) {
            throw new IllegalArgumentException("Pending operation requires a nonzero checked balance delta");
        }
        Objects.requireNonNull(status, "status");
        lastAttemptId = Objects.requireNonNull(lastAttemptId, "lastAttemptId");
        quarantineReason = Objects.requireNonNull(quarantineReason, "quarantineReason")
                .map(String::strip);
        if (status == PendingOperationStatus.PENDING && quarantineReason.isPresent()) {
            throw new IllegalArgumentException("Pending operation cannot carry a quarantine reason");
        }
        if (status == PendingOperationStatus.QUARANTINED
                && (quarantineReason.isEmpty() || quarantineReason.orElseThrow().isEmpty())) {
            throw new IllegalArgumentException("Quarantined operation requires a reason");
        }
        if (quarantineReason.isPresent()
                && quarantineReason.orElseThrow().length() > MAX_QUARANTINE_REASON) {
            throw new IllegalArgumentException("Offline-operation quarantine reason is too long");
        }
    }

    public static PendingProgressionOperation pending(
            UUID operationId,
            UUID targetId,
            UUID issuerId,
            Instant createdAt,
            Instant expiresAt,
            DefinitionRevision definitionRevision,
            ResourceLocation balanceId,
            long delta,
            long minimum,
            long maximum,
            boolean rewardEligible
    ) {
        return new PendingProgressionOperation(
                operationId,
                targetId,
                issuerId,
                createdAt,
                expiresAt,
                definitionRevision,
                balanceId,
                delta,
                minimum,
                maximum,
                rewardEligible,
                PendingOperationStatus.PENDING,
                Optional.empty(),
                Optional.empty()
        );
    }

    public PendingProgressionOperation attempted(UUID attemptId) {
        return new PendingProgressionOperation(
                operationId, targetId, issuerId, createdAt, expiresAt, definitionRevision,
                balanceId, delta, minimum, maximum, rewardEligible, status,
                Optional.of(attemptId), quarantineReason
        );
    }

    public PendingProgressionOperation rebase(
            DefinitionRevision currentDefinition,
            ResourceLocation resolvedBalanceId
    ) {
        return new PendingProgressionOperation(
                operationId, targetId, issuerId, createdAt, expiresAt, currentDefinition,
                resolvedBalanceId, delta, minimum, maximum, rewardEligible, status,
                lastAttemptId, quarantineReason
        );
    }

    public PendingProgressionOperation quarantine(String reason) {
        String bounded = Objects.requireNonNull(reason, "reason").strip();
        if (bounded.length() > MAX_QUARANTINE_REASON) {
            bounded = bounded.substring(0, MAX_QUARANTINE_REASON);
        }
        return new PendingProgressionOperation(
                operationId, targetId, issuerId, createdAt, expiresAt, definitionRevision,
                balanceId, delta, minimum, maximum, rewardEligible,
                PendingOperationStatus.QUARANTINED, lastAttemptId, Optional.of(bounded)
        );
    }

    @Override
    public int compareTo(PendingProgressionOperation other) {
        int created = createdAt.compareTo(other.createdAt);
        return created != 0 ? created : operationId.compareTo(other.operationId);
    }
}
