package com.envisione.progressiveskills.common.source;

import java.util.Objects;

/**
 * An immutable half-open source range: {@code [start, end)}.
 * Zero-width spans are valid and are useful for insertion diagnostics.
 *
 * @param start inclusive start position
 * @param end exclusive end position
 */
public record SourceSpan(SourcePosition start, SourcePosition end) {
    public SourceSpan {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Source span ends before it starts: " + start + " -> " + end);
        }
    }

    public static SourceSpan between(int startLine, int startColumn, int endLine, int endColumn) {
        return new SourceSpan(
                new SourcePosition(startLine, startColumn),
                new SourcePosition(endLine, endColumn)
        );
    }

    public boolean isEmpty() {
        return start.equals(end);
    }

    public boolean contains(SourcePosition position) {
        Objects.requireNonNull(position, "position");
        return start.compareTo(position) <= 0 && position.compareTo(end) < 0;
    }

    public SourceSpan enclosing(SourceSpan other) {
        Objects.requireNonNull(other, "other");
        SourcePosition first = start.compareTo(other.start) <= 0 ? start : other.start;
        SourcePosition last = end.compareTo(other.end) >= 0 ? end : other.end;
        return new SourceSpan(first, last);
    }
}
