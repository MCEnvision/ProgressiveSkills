package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;

/** One source's contribution to a persistent entitlement. */
public record EntitlementContribution(long value, EntitlementResolver resolver) {
    public EntitlementContribution {
        Objects.requireNonNull(resolver, "resolver");
        if (resolver == EntitlementResolver.BOOLEAN_UNION && value != 0 && value != 1) {
            throw new IllegalArgumentException("Boolean-union contributions must be 0 or 1");
        }
    }
}
