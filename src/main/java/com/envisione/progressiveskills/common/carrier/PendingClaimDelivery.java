package com.envisione.progressiveskills.common.carrier;

@FunctionalInterface
public interface PendingClaimDelivery {
    boolean deliver(PendingCarrierClaim claim);
}
