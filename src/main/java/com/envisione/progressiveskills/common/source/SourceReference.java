package com.envisione.progressiveskills.common.source;

import java.util.Objects;

/** Couples a source document's provenance to one half-open span within it. */
public record SourceReference(Provenance provenance, SourceSpan span) implements Comparable<SourceReference> {
    public SourceReference {
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(span, "span");
    }

    @Override
    public int compareTo(SourceReference other) {
        int provenanceComparison = provenance.compareTo(other.provenance);
        if (provenanceComparison != 0) {
            return provenanceComparison;
        }
        int startComparison = span.start().compareTo(other.span.start());
        return startComparison != 0 ? startComparison : span.end().compareTo(other.span.end());
    }
}
