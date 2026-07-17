package com.envisione.progressiveskills.common.requirement;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementEvaluatorTest {
    @Test
    void comparisonsMissingPoliciesAndExplanationsAreTyped() {
        var expression = new RequirementExpression.All(List.of(
                skill("test:mining", ComparisonOperator.GREATER_THAN_OR_EQUAL, 4, false),
                currency("test:points", ComparisonOperator.LESS_THAN, 10, false)
        ));
        var compiled = CompiledRequirement.compile(expression);
        var evaluation = compiled.evaluate(dependency -> switch (dependency.kind()) {
            case SKILL_LEVEL -> RequirementContext.Lookup.present(5);
            case CURRENCY -> RequirementContext.Lookup.present(3);
        });
        assertTrue(evaluation.passed());
        assertEquals(2, evaluation.steps().size());
        assertTrue(evaluation.firstFailure().isEmpty());

        var missingPass = CompiledRequirement.compile(skill(
                "test:mining", ComparisonOperator.EQUAL, 1, true
        )).evaluate(ignored -> RequirementContext.Lookup.missing());
        assertTrue(missingPass.passed());
        var missingFail = CompiledRequirement.compile(skill(
                "test:mining", ComparisonOperator.EQUAL, 1, false
        )).evaluate(ignored -> RequirementContext.Lookup.missing());
        assertFalse(missingFail.passed());
        assertTrue(missingFail.firstFailure().orElseThrow().contains("missing"));
    }

    @Test
    void allAnyAndNotShortCircuitDeterministically() {
        var lookups = new AtomicInteger();
        RequirementContext context = dependency -> {
            lookups.incrementAndGet();
            return RequirementContext.Lookup.present(dependency.id().getPath().equals("yes") ? 1 : 0);
        };
        var all = CompiledRequirement.compile(new RequirementExpression.All(List.of(
                skill("test:no", ComparisonOperator.EQUAL, 1, false),
                skill("test:yes", ComparisonOperator.EQUAL, 1, false)
        )));
        assertFalse(all.evaluate(context).passed());
        assertEquals(1, lookups.get());

        lookups.set(0);
        var any = CompiledRequirement.compile(new RequirementExpression.Any(List.of(
                skill("test:yes", ComparisonOperator.EQUAL, 1, false),
                skill("test:no", ComparisonOperator.EQUAL, 1, false)
        )));
        assertTrue(any.evaluate(context).passed());
        assertEquals(1, lookups.get());
        assertFalse(CompiledRequirement.compile(new RequirementExpression.Not(
                skill("test:yes", ComparisonOperator.EQUAL, 1, false)
        )).evaluate(context).passed());

        var nested = CompiledRequirement.compile(new RequirementExpression.All(List.of(
                new RequirementExpression.Not(skill(
                        "test:no", ComparisonOperator.EQUAL, 1, false
                )),
                currency("test:points", ComparisonOperator.EQUAL, 1, false)
        ))).evaluate(context);
        assertFalse(nested.passed());
        assertTrue(nested.firstFailure().orElseThrow().startsWith("currency:test:points"));
    }

    @Test
    void dependencyExtractionAndIndexOrderingAreStableAndBounded() {
        RequirementExpression expression = new RequirementExpression.All(List.of(
                currency("test:z", ComparisonOperator.EQUAL, 0, false),
                skill("test:a", ComparisonOperator.EQUAL, 0, false)
        ));
        var compiled = CompiledRequirement.compile(expression);
        assertEquals(List.of(
                new RequirementDependency(RequirementDependency.Kind.SKILL_LEVEL, id("test:a")),
                new RequirementDependency(RequirementDependency.Kind.CURRENCY, id("test:z"))
        ), compiled.dependencies());

        var expressions = new LinkedHashMap<String, RequirementExpression>();
        expressions.put("zeta", expression);
        expressions.put("alpha", skill("test:a", ComparisonOperator.EQUAL, 0, false));
        var index = RequirementDependencyIndex.compile(expressions, Comparator.naturalOrder(), 3);
        assertEquals(List.of("alpha", "zeta"), index.consumers(
                new RequirementDependency(RequirementDependency.Kind.SKILL_LEVEL, id("test:a"))
        ));
        assertEquals(3, index.edgeCount());
        assertThrows(IllegalArgumentException.class, () -> RequirementDependencyIndex.compile(
                expressions, Comparator.naturalOrder(), 2
        ));
    }

    @Test
    void emptyAndLimitsFailClosedWithoutContextReads() {
        var reads = new AtomicInteger();
        assertTrue(CompiledRequirement.compile(RequirementExpression.always()).evaluate(dependency -> {
            reads.incrementAndGet();
            return RequirementContext.Lookup.missing();
        }).passed());
        assertEquals(0, reads.get());

        RequirementExpression deep = skill("test:a", ComparisonOperator.EQUAL, 0, false);
        for (int index = 0; index < 17; index++) {
            deep = new RequirementExpression.Not(deep);
        }
        RequirementExpression tooDeep = deep;
        assertThrows(IllegalArgumentException.class, () -> CompiledRequirement.compile(tooDeep));
    }

    private static RequirementExpression skill(
            String id,
            ComparisonOperator operator,
            long value,
            boolean missing
    ) {
        return new RequirementExpression.SkillLevel(id(id), operator, value, missing);
    }

    private static RequirementExpression currency(
            String id,
            ComparisonOperator operator,
            long value,
            boolean missing
    ) {
        return new RequirementExpression.Currency(id(id), operator, value, missing);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
