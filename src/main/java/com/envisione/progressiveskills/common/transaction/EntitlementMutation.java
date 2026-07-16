package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;
import java.util.Optional;

/** Grant, replace, or revoke one source-owned persistent contribution. */
public record EntitlementMutation(
        EntitlementKey key,
        GrantSourceId source,
        Optional<EntitlementContribution> contribution
) {
    public EntitlementMutation {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(contribution, "contribution");
    }

    public static EntitlementMutation grant(
            EntitlementKey key,
            GrantSourceId source,
            long value,
            EntitlementResolver resolver
    ) {
        return new EntitlementMutation(key, source, Optional.of(new EntitlementContribution(value, resolver)));
    }

    public static EntitlementMutation revoke(EntitlementKey key, GrantSourceId source) {
        return new EntitlementMutation(key, source, Optional.empty());
    }
}
