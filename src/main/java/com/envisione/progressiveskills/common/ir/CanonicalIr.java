package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable canonical IR snapshot consumed by Phase 3 staging and live publication. */
public final class CanonicalIr {
    private final Map<DefinitionKey, CanonicalDefinition> definitions;
    private final AliasMap aliases;

    private CanonicalIr(Map<DefinitionKey, CanonicalDefinition> definitions, AliasMap aliases) {
        this.definitions = definitions;
        this.aliases = aliases;
    }

    public static CanonicalIr of(Collection<CanonicalDefinition> definitions, AliasMap aliases) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(aliases, "aliases");
        var sorted = new TreeMap<DefinitionKey, CanonicalDefinition>();
        for (var definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            var key = definition.header().key();
            var previous = sorted.putIfAbsent(key, definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate canonical definition: " + key);
            }
        }
        return new CanonicalIr(
                Collections.unmodifiableMap(new LinkedHashMap<>(sorted)),
                aliases
        );
    }

    public Map<DefinitionKey, CanonicalDefinition> definitions() {
        return definitions;
    }

    public AliasMap aliases() {
        return aliases;
    }

    public SemanticIr semanticProjection() {
        var semantic = new TreeMap<DefinitionKey, SemanticDefinition>();
        definitions.forEach((key, definition) -> semantic.put(key, definition.semanticProjection()));
        return new SemanticIr(semantic, aliases);
    }
}
