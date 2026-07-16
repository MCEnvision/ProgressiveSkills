package com.envisione.progressiveskills.common.data;

/** Counts from one deterministic alias/orphan/restore reconciliation pass. */
public record DefinitionReconciliationReport(int renamed, int orphaned, int restored, int active) {
    public DefinitionReconciliationReport {
        if (renamed < 0 || orphaned < 0 || restored < 0 || active < 0) {
            throw new IllegalArgumentException("Reconciliation counts must not be negative");
        }
    }
}
