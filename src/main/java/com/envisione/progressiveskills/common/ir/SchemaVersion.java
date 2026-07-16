package com.envisione.progressiveskills.common.ir;

/** Explicit authoring schema version carried into every canonical definition. */
public record SchemaVersion(int value) implements Comparable<SchemaVersion> {
    public static final SchemaVersion V2 = new SchemaVersion(2);
    public static final SchemaVersion CURRENT = V2;

    public SchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
    }

    @Override
    public int compareTo(SchemaVersion other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
