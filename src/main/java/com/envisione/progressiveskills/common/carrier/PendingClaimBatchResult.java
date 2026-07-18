package com.envisione.progressiveskills.common.carrier;

public record PendingClaimBatchResult(int delivered, int retained) {
    public PendingClaimBatchResult {
        if (delivered < 0 || retained < 0) {
            throw new IllegalArgumentException("Pending claim batch counts must not be negative");
        }
    }
}
