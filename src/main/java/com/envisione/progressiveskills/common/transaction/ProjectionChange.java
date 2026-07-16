package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;
import java.util.OptionalLong;

/** Effective persistent-value change after all owners have been resolved. */
public record ProjectionChange(EntitlementKey key, OptionalLong before, OptionalLong after)
        implements Comparable<ProjectionChange> {
    public ProjectionChange {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (before.equals(after)) {
            throw new IllegalArgumentException("Projection change must change the effective value");
        }
    }

    @Override
    public int compareTo(ProjectionChange other) {
        return key.compareTo(other.key);
    }
}
