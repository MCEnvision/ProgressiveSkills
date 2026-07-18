package com.envisione.progressiveskills.common.creator;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CreatorPredicateEngine {
    private CreatorPredicateEngine() {
    }

    public static Result evaluate(
            CreatorCatalog catalog,
            ResourceLocation predicateId,
            Context context
    ) {
        var trace = new java.util.ArrayList<String>();
        boolean passed = evaluate(catalog, predicateId, context, new HashSet<>(), trace);
        return new Result(passed, trace);
    }

    private static boolean evaluate(
            CreatorCatalog catalog,
            ResourceLocation predicateId,
            Context context,
            Set<ResourceLocation> visiting,
            java.util.List<String> trace
    ) {
        if (!visiting.add(predicateId)) {
            throw new IllegalArgumentException("Predicate graph contains a cycle at " + predicateId);
        }
        CreatorDefinition definition = catalog.definition(DefinitionKinds.PREDICATE, predicateId).orElseThrow(
                () -> new IllegalArgumentException("Unknown predicate " + predicateId));
        String type = definition.text("type").orElse("all");
        boolean result = switch (type) {
            case "all" -> definition.ids("predicates").stream()
                    .allMatch(id -> evaluate(catalog, id, context, visiting, trace));
            case "any" -> definition.ids("predicates").stream()
                    .anyMatch(id -> evaluate(catalog, id, context, visiting, trace));
            case "not" -> !evaluate(catalog, definition.id("predicate").orElseThrow(),
                    context, visiting, trace);
            case "flag" -> context.flags().contains(definition.id("flag").orElseThrow());
            case "value" -> compare(
                    context.values().getOrDefault(definition.id("value").orElseThrow(), 0L),
                    definition.text("operator").orElse("greater_or_equal"),
                    definition.integer("threshold").orElse(0L));
            default -> throw new IllegalArgumentException("Unknown predicate type " + type);
        };
        trace.add(predicateId + ". " + result + ".");
        visiting.remove(predicateId);
        return result;
    }

    private static boolean compare(long value, String operator, long threshold) {
        return switch (operator) {
            case "equal" -> value == threshold;
            case "not_equal" -> value != threshold;
            case "less" -> value < threshold;
            case "less_or_equal" -> value <= threshold;
            case "greater" -> value > threshold;
            case "greater_or_equal" -> value >= threshold;
            default -> throw new IllegalArgumentException("Unknown predicate comparison " + operator);
        };
    }

    public record Context(Map<ResourceLocation, Long> values, Set<ResourceLocation> flags) {
        public Context {
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
            flags = Set.copyOf(Objects.requireNonNull(flags, "flags"));
        }
    }

    public record Result(boolean passed, java.util.List<String> trace) {
        public Result {
            trace = java.util.List.copyOf(trace);
        }
    }
}
