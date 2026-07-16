package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable semantic projection suitable for adapter-parity comparisons. */
public record SemanticIr(Map<DefinitionKey, SemanticDefinition> definitions, AliasMap aliases) {
    public SemanticIr {
        definitions = sortedCopy(definitions);
        Objects.requireNonNull(aliases, "aliases");
    }

    private static Map<DefinitionKey, SemanticDefinition> sortedCopy(
            Map<DefinitionKey, SemanticDefinition> source
    ) {
        Objects.requireNonNull(source, "definitions");
        var sorted = new TreeMap<DefinitionKey, SemanticDefinition>();
        source.forEach((key, value) -> {
            var checkedKey = Objects.requireNonNull(key, "definition key");
            var checkedValue = Objects.requireNonNull(value, "semantic definition");
            if (!checkedKey.equals(checkedValue.header().key())) {
                throw new IllegalArgumentException(
                        "Semantic definition key does not match its header: "
                                + checkedKey + " != " + checkedValue.header().key()
                );
            }
            sorted.put(checkedKey, checkedValue);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
