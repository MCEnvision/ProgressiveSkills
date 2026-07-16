package com.envisione.progressiveskills.common.ir;

import java.util.Objects;

/** Adapter-neutral definition meaning with all source/provenance data excluded. */
public record SemanticDefinition(DefinitionHeader header, CanonicalValue.ObjectValue fields)
        implements Comparable<SemanticDefinition> {
    public SemanticDefinition {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public int compareTo(SemanticDefinition other) {
        return header.compareTo(other.header);
    }
}
