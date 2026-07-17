package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.requirement.CompiledRequirement;
import com.envisione.progressiveskills.common.requirement.RequirementExpression;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record RuleDefinition(
        ResourceLocation id,
        boolean enabled,
        ResourceLocation trigger,
        int priority,
        ResourceLocation stackGroup,
        RuleStackRule stackRule,
        String credit,
        List<RuleMatcherSpec> matchers,
        boolean customNameAllowed,
        RequirementExpression requirements,
        long baseUnits,
        ExpressionRounding rounding,
        List<RuleMultiplier> multipliers,
        RuleAntiExploit antiExploit,
        XpOutput output
) {
    public static final int MAX_MATCHERS = 64;
    public static final int MAX_MULTIPLIERS = 32;

    public RuleDefinition {
        id = StableId.requireValid(id);
        trigger = StableId.requireValid(trigger);
        stackGroup = StableId.requireValid(stackGroup);
        Objects.requireNonNull(stackRule, "stackRule");
        credit = Objects.requireNonNull(credit, "credit");
        if (!credit.equals("actor")) {
            throw new IllegalArgumentException("Phase 9 rules require actor credit");
        }
        matchers = List.copyOf(Objects.requireNonNull(matchers, "matchers"));
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(rounding, "rounding");
        multipliers = List.copyOf(Objects.requireNonNull(multipliers, "multipliers"));
        Objects.requireNonNull(antiExploit, "antiExploit");
        Objects.requireNonNull(output, "output");
        if (matchers.size() > MAX_MATCHERS || multipliers.size() > MAX_MULTIPLIERS) {
            throw new IllegalArgumentException("Rule matcher or multiplier count exceeds its bound");
        }
        if (new HashSet<>(multipliers.stream().map(RuleMultiplier::id).toList()).size() != multipliers.size()) {
            throw new IllegalArgumentException("Duplicate rule multiplier id");
        }
        if (baseUnits <= 0) {
            throw new IllegalArgumentException("Rule base amount must be positive");
        }
        CompiledRequirement.compile(requirements);
        RuleMultiplierEngine.apply(baseUnits, multipliers, rounding);
    }

    public long multipliedBaseUnits() {
        return RuleMultiplierEngine.apply(baseUnits, multipliers, rounding);
    }

    public record XpOutput(ResourceLocation id, ResourceLocation skill) {
        public XpOutput {
            id = StableId.requireValid(id);
            skill = StableId.requireValid(skill);
        }
    }
}
