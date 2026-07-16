package com.envisione.progressiveskills.common.id;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A typed canonical definition identity. Natural ordering is kind-first and namespace-first. */
public record DefinitionKey(DefinitionKind kind, ResourceLocation id) implements Comparable<DefinitionKey> {
    public DefinitionKey {
        Objects.requireNonNull(kind, "kind");
        id = StableId.requireValid(id);
    }

    public static DefinitionKey parse(DefinitionKind kind, String id) {
        return new DefinitionKey(kind, StableId.parse(id));
    }

    @Override
    public int compareTo(DefinitionKey other) {
        int kindComparison = kind.compareTo(other.kind);
        return kindComparison != 0 ? kindComparison : id.compareNamespaced(other.id);
    }

    @Override
    public String toString() {
        return kind + "[" + id + "]";
    }
}
