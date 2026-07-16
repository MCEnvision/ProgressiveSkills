package com.envisione.progressiveskills.common.transaction;

/** Honest external-delivery guarantee attached to a transition action. */
public enum DeliveryContract {
    EFFECTIVELY_ONCE,
    AT_LEAST_ONCE,
    BEST_EFFORT
}
