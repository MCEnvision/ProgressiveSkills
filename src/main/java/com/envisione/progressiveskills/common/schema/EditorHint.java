package com.envisione.progressiveskills.common.schema;

import java.util.Objects;

/** Presentation-neutral form metadata generated for future authoring tools. */
public record EditorHint(EditorWidget widget, String group, int order, String help) {
    public EditorHint {
        Objects.requireNonNull(widget, "widget");
        group = requireText(group, "group");
        help = requireText(help, "help");
        if (order < 0) {
            throw new IllegalArgumentException("order must be non-negative");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
