package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;
import java.util.Optional;

public record PaidCostMutation(
        PurchaseInstanceId instanceId,
        Optional<PaidCostRecord> expected,
        Optional<PaidCostRecord> replacement
) {
    public PaidCostMutation {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(replacement, "replacement");
        expected.ifPresent(record -> requireMatching(instanceId, record));
        replacement.ifPresent(record -> requireMatching(instanceId, record));
        if (expected.equals(replacement)) {
            throw new IllegalArgumentException("Paid cost mutation must change the record");
        }
        if (expected.isPresent() && replacement.isPresent()) {
            requireSameHistoricalEvidence(expected.orElseThrow(), replacement.orElseThrow());
        }
    }

    public static PaidCostMutation insert(PaidCostRecord record) {
        Objects.requireNonNull(record, "record");
        return new PaidCostMutation(record.instanceId(), Optional.empty(), Optional.of(record));
    }

    public static PaidCostMutation remove(PaidCostRecord record) {
        Objects.requireNonNull(record, "record");
        return new PaidCostMutation(record.instanceId(), Optional.of(record), Optional.empty());
    }

    public static PaidCostMutation replace(PaidCostRecord expected, PaidCostRecord replacement) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(replacement, "replacement");
        if (!expected.instanceId().equals(replacement.instanceId())) {
            throw new IllegalArgumentException("Paid cost replacement identity must not change");
        }
        return new PaidCostMutation(expected.instanceId(), Optional.of(expected), Optional.of(replacement));
    }

    private static void requireMatching(PurchaseInstanceId instanceId, PaidCostRecord record) {
        if (!instanceId.equals(record.instanceId())) {
            throw new IllegalArgumentException("Paid cost record identity does not match its mutation");
        }
    }

    private static void requireSameHistoricalEvidence(PaidCostRecord expected, PaidCostRecord replacement) {
        if (!expected.purchaseTransactionId().equals(replacement.purchaseTransactionId())
                || !expected.definitionRevision().equals(replacement.definitionRevision())
                || !expected.ownerLineage().equals(replacement.ownerLineage())
                || !expected.paidBalances().equals(replacement.paidBalances())) {
            throw new IllegalArgumentException("Paid cost historical evidence must not be replaced");
        }
    }
}
