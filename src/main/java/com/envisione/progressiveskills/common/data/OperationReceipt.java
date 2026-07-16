package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.transaction.TransactionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable same-attachment proof for death and pending-offline operations. */
public record OperationReceipt(
        UUID operationId,
        String operationType,
        TransactionId transactionId,
        long resultingStateRevision,
        Instant appliedAt,
        String detail
) {
    public static final int MAX_DETAIL_LENGTH = 512;

    public OperationReceipt {
        Objects.requireNonNull(operationId, "operationId");
        operationType = Objects.requireNonNull(operationType, "operationType").strip();
        if (operationType.isEmpty() || operationType.length() > 64
                || operationType.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Operation type must be bounded printable text");
        }
        Objects.requireNonNull(transactionId, "transactionId");
        if (resultingStateRevision < 0) {
            throw new IllegalArgumentException("Operation state revision must not be negative");
        }
        Objects.requireNonNull(appliedAt, "appliedAt");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty() || detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Operation receipt detail must be bounded");
        }
    }
}
