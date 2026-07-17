package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.expression.CompiledNumericExpression;
import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.expression.NumericExpression;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RuleMultiplierEngine {
    private RuleMultiplierEngine() {
    }

    public static long apply(long baseUnits, List<RuleMultiplier> multipliers) {
        return apply(baseUnits, multipliers, ExpressionRounding.FLOOR);
    }

    public static long apply(
            long baseUnits,
            List<RuleMultiplier> multipliers,
            ExpressionRounding rounding
    ) {
        if (baseUnits <= 0) {
            throw new IllegalArgumentException("Rule base amount must be positive");
        }
        var factors = new ArrayList<NumericExpression>();
        factors.add(new NumericExpression.Constant(baseUnits));
        var byStage = new TreeMap<RuleMultiplierStage, Map<ResourceLocation, List<RuleMultiplier>>>();
        for (RuleMultiplier multiplier : multipliers) {
            byStage.computeIfAbsent(multiplier.stage(), ignored -> new TreeMap<>(ResourceLocation::compareNamespaced))
                    .computeIfAbsent(multiplier.group(), ignored -> new ArrayList<>())
                    .add(multiplier);
        }
        for (RuleMultiplierStage stage : RuleMultiplierStage.values()) {
            Map<ResourceLocation, List<RuleMultiplier>> groups = byStage.getOrDefault(stage, Map.of());
            for (List<RuleMultiplier> group : groups.values()) {
                for (long factor : resolveGroup(group)) {
                    if (factor <= 0) {
                        throw new IllegalArgumentException("Resolved rule multiplier must be positive");
                    }
                    factors.add(new NumericExpression.Constant(factor));
                }
            }
        }
        long result = CompiledNumericExpression.compile(
                new NumericExpression.Product(factors), rounding
        ).constantValue().orElseThrow();
        if (result <= 0) {
            throw new IllegalArgumentException("Rule multipliers reduced the award to zero");
        }
        return result;
    }

    private static List<Long> resolveGroup(List<RuleMultiplier> group) {
        RuleMultiplierMode mode = group.getFirst().mode();
        if (group.stream().anyMatch(multiplier -> multiplier.mode() != mode)) {
            throw new IllegalArgumentException("A multiplier stack group must use one mode");
        }
        return switch (mode) {
            case ADD -> List.of(Math.addExact(FixedPoint.SCALE,
                    group.stream().mapToLong(RuleMultiplier::valueUnits).reduce(0, Math::addExact)));
            case MULTIPLY -> group.stream().map(RuleMultiplier::valueUnits).toList();
            case HIGHEST -> List.of(group.stream().mapToLong(RuleMultiplier::valueUnits).max().orElseThrow());
            case LOWEST -> List.of(group.stream().mapToLong(RuleMultiplier::valueUnits).min().orElseThrow());
            case REPLACE -> List.of(group.stream().sorted(
                    Comparator.comparingInt(RuleMultiplier::priority).reversed()
                            .thenComparing(RuleMultiplier::id, ResourceLocation::compareNamespaced)
            ).findFirst().orElseThrow().valueUnits());
        };
    }

    public static long scale(long amountUnits, long factorUnits) {
        if (amountUnits < 0 || factorUnits < 0) {
            throw new IllegalArgumentException("Rule scaling values must not be negative");
        }
        return CompiledNumericExpression.compile(
                new NumericExpression.Multiply(
                        new NumericExpression.Constant(amountUnits),
                        new NumericExpression.Constant(factorUnits)
                ),
                ExpressionRounding.FLOOR
        ).constantValue().orElseThrow();
    }
}
