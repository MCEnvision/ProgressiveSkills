package com.envisione.progressiveskills.common.transaction;

/** Deterministic lifecycle disposition for one planned transition action. */
public enum ActionDisposition {
    EXECUTED,
    SKIPPED_EXISTING_RECEIPT,
    SKIPPED_AFTER_FAILURE,
    FAILED
}
