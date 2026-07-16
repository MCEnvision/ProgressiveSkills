package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.id.DefinitionKey;

import java.util.Objects;
import java.util.Optional;

/** Immutable identity, version, and presentation header for canonical definitions. */
public record DefinitionHeader(
        SchemaVersion schemaVersion,
        DefinitionKey key,
        Optional<DefinitionPresentation> presentation
) implements Comparable<DefinitionHeader> {
    public DefinitionHeader {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(presentation, "presentation");
    }

    public DefinitionHeader(
            SchemaVersion schemaVersion,
            DefinitionKey key,
            DefinitionPresentation presentation
    ) {
        this(schemaVersion, key, Optional.of(Objects.requireNonNull(presentation, "presentation")));
    }

    public static DefinitionHeader withoutPresentation(SchemaVersion schemaVersion, DefinitionKey key) {
        return new DefinitionHeader(schemaVersion, key, Optional.empty());
    }

    @Override
    public int compareTo(DefinitionHeader other) {
        return key.compareTo(other.key);
    }
}
