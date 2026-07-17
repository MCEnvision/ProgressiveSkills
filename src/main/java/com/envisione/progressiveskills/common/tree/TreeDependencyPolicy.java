package com.envisione.progressiveskills.common.tree;

import java.util.Locale;

public enum TreeDependencyPolicy {
    CASCADE_REFUND;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TreeDependencyPolicy parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Core trees require cascade_refund dependency policy", exception);
        }
    }
}
