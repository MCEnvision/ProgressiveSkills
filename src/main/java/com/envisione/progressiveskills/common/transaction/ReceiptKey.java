package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;

/** Exact receipt identity; permanent scopes are never evicted by ordinary retention. */
public record ReceiptKey(GrantSourceId source, RepeatPolicy policy, String scope)
        implements Comparable<ReceiptKey> {
    public static final int MAX_SCOPE_LENGTH = 192;

    public ReceiptKey {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(policy, "policy");
        scope = Objects.requireNonNull(scope, "scope");
        if (policy == RepeatPolicy.ALWAYS) {
            throw new IllegalArgumentException("Always-repeat actions do not use receipts");
        }
        if (scope.isBlank() || scope.length() > MAX_SCOPE_LENGTH || scope.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid receipt scope");
        }
    }

    @Override
    public int compareTo(ReceiptKey other) {
        int sourceOrder = source.compareTo(other.source);
        if (sourceOrder != 0) {
            return sourceOrder;
        }
        int policyOrder = policy.compareTo(other.policy);
        return policyOrder != 0 ? policyOrder : scope.compareTo(other.scope);
    }
}
