package com.envisione.progressiveskills.common.transaction;

/** Terminal transaction state returned to every caller and replay. */
public enum TransactionStatus {
    COMMITTED,
    COMMITTED_WITH_ACTION_FAILURES,
    REJECTED;

    public boolean committed() {
        return this != REJECTED;
    }
}
