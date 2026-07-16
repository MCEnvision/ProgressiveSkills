package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;

import java.util.ArrayDeque;
import java.util.Objects;

/** Immutable canonical definition paired with non-semantic source information. */
public record CanonicalDefinition(
        DefinitionHeader header,
        CanonicalValue.ObjectValue fields,
        Provenance provenance,
        SourceMap sourceMap
) implements Comparable<CanonicalDefinition> {
    public static final int MAX_VALUE_DEPTH = 64;
    public static final int MAX_VALUE_NODES = 100_000;
    public static final int MAX_AGGREGATE_TEXT_CODE_POINTS = 1_000_000;

    public CanonicalDefinition {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(sourceMap, "sourceMap");
        validateValueTree(header, fields);
    }

    public SemanticDefinition semanticProjection() {
        return new SemanticDefinition(header, fields);
    }

    @Override
    public int compareTo(CanonicalDefinition other) {
        return header.compareTo(other.header);
    }

    private static void validateValueTree(DefinitionHeader header, CanonicalValue.ObjectValue root) {
        var pending = new ArrayDeque<ValueAtDepth>();
        pending.addLast(new ValueAtDepth(root, 1));
        int nodes = 0;
        long textCodePoints = headerTextCost(header);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            nodes++;
            if (nodes > MAX_VALUE_NODES) {
                throw new IllegalArgumentException("Canonical definition exceeds " + MAX_VALUE_NODES + " values");
            }
            if (current.depth() > MAX_VALUE_DEPTH) {
                throw new IllegalArgumentException("Canonical definition exceeds value depth " + MAX_VALUE_DEPTH);
            }
            textCodePoints += valueTextCost(current.value());
            if (textCodePoints > MAX_AGGREGATE_TEXT_CODE_POINTS) {
                throw new IllegalArgumentException(
                        "Canonical definition exceeds aggregate text budget "
                                + MAX_AGGREGATE_TEXT_CODE_POINTS + " code points"
                );
            }
            switch (current.value()) {
                case CanonicalValue.ObjectValue object -> object.fields().values()
                        .forEach(value -> pending.addLast(new ValueAtDepth(value, current.depth() + 1)));
                case CanonicalValue.ListValue list -> list.values()
                        .forEach(value -> pending.addLast(new ValueAtDepth(value, current.depth() + 1)));
                default -> {
                    // Scalar values have no children.
                }
            }
        }
    }

    private static long headerTextCost(DefinitionHeader header) {
        long cost = codePointLength(header.schemaVersion().toString())
                + codePointLength(header.key().kind().id().toString())
                + codePointLength(header.key().id().toString());
        if (header.presentation().isPresent()) {
            var presentation = header.presentation().orElseThrow();
            cost += componentTextCost(presentation.display());
            if (presentation.description().isPresent()) {
                cost += componentTextCost(presentation.description().orElseThrow());
            }
            cost += iconTextCost(presentation.icon());
            for (var alias : presentation.searchAliases()) {
                cost += codePointLength(alias);
            }
        }
        return cost;
    }

    private static long valueTextCost(CanonicalValue value) {
        return switch (value) {
            case CanonicalValue.BooleanValue ignored -> 0;
            case CanonicalValue.IntegerValue ignored -> 0;
            case CanonicalValue.DecimalValue decimal -> codePointLength(decimal.value().toPlainString());
            case CanonicalValue.TextValue text -> codePointLength(text.value());
            case CanonicalValue.IdValue id -> codePointLength(id.value().toString());
            case CanonicalValue.ReferenceValue reference ->
                    codePointLength(reference.value().kind().id().toString())
                            + codePointLength(reference.value().id().toString());
            case CanonicalValue.ComponentValue component -> componentTextCost(component.value());
            case CanonicalValue.IconValue icon -> iconTextCost(icon.value());
            case CanonicalValue.ListValue ignored -> 0;
            case CanonicalValue.ObjectValue object -> object.fields().keySet().stream()
                    .mapToLong(CanonicalDefinition::codePointLength)
                    .sum();
        };
    }

    private static long iconTextCost(IconSpec icon) {
        long cost = codePointLength(icon.kind().serializedName())
                + codePointLength(icon.fallback().toString())
                + componentTextCost(icon.altText());
        for (var reference : icon.references()) {
            cost += codePointLength(reference.toString());
        }
        if (icon.narration().isPresent()) {
            cost += componentTextCost(icon.narration().orElseThrow());
        }
        return cost;
    }

    private static long componentTextCost(ComponentSpec root) {
        long cost = 0;
        var pending = new ArrayDeque<ComponentSpec>();
        pending.addLast(root);
        while (!pending.isEmpty()) {
            var component = pending.removeFirst();
            cost += codePointLength(component.fallback());
            if (component.localizationKey().isPresent()) {
                cost += codePointLength(component.localizationKey().orElseThrow());
            }
            for (var placeholder : component.placeholders().entrySet()) {
                cost += codePointLength(placeholder.getKey())
                        + codePointLength(placeholder.getValue().name());
            }
            if (component.style().color().isPresent()) {
                cost += codePointLength(component.style().color().orElseThrow().value());
            }
            if (component.style().font().isPresent()) {
                cost += codePointLength(component.style().font().orElseThrow().toString());
            }
            for (var decoration : component.style().decorations()) {
                cost += codePointLength(decoration.name());
            }
            component.children().forEach(pending::addLast);
        }
        return cost;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private record ValueAtDepth(CanonicalValue value, int depth) {
        private ValueAtDepth {
            Objects.requireNonNull(value, "value");
        }
    }
}
