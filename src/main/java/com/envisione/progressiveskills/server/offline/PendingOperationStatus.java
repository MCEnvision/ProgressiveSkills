package com.envisione.progressiveskills.server.offline;

/** Durable queue state for a pending offline player mutation. */
public enum PendingOperationStatus {
    PENDING,
    QUARANTINED
}
