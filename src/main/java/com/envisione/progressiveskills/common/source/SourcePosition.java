package com.envisione.progressiveskills.common.source;

/**
 * A one-based position in an authoring source.
 *
 * @param line one-based line number
 * @param column one-based column number in the originating adapter's coordinate system
 */
public record SourcePosition(int line, int column) implements Comparable<SourcePosition> {
    public SourcePosition {
        if (line < 1) {
            throw new IllegalArgumentException("Source line must be at least 1: " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("Source column must be at least 1: " + column);
        }
    }

    @Override
    public int compareTo(SourcePosition other) {
        int lineComparison = Integer.compare(line, other.line);
        return lineComparison != 0 ? lineComparison : Integer.compare(column, other.column);
    }
}
