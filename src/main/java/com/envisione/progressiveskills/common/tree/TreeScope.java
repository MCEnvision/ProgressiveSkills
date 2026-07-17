package com.envisione.progressiveskills.common.tree;

import java.util.Locale;

public enum TreeScope {
    GLOBAL,
    SKILL;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TreeScope parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Core trees support only global or skill scope", exception);
        }
    }
}
