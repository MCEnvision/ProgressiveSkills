package com.envisione.progressiveskills.common.id;

import java.util.Objects;

/** One proposed old-definition-key to replacement-definition-key mapping. */
public record Alias(DefinitionKey source, DefinitionKey target) implements Comparable<Alias> {
    public Alias {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public int compareTo(Alias other) {
        int sourceComparison = source.compareTo(other.source);
        return sourceComparison != 0 ? sourceComparison : target.compareTo(other.target);
    }
}
