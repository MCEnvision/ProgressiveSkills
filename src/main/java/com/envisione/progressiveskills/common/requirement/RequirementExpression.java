package com.envisione.progressiveskills.common.requirement;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public sealed interface RequirementExpression permits RequirementExpression.All, RequirementExpression.Any,
        RequirementExpression.Not, RequirementExpression.SkillLevel, RequirementExpression.Currency {
    record All(List<RequirementExpression> children) implements RequirementExpression {
        public All {
            children = List.copyOf(Objects.requireNonNull(children, "children"));
        }
    }

    record Any(List<RequirementExpression> children) implements RequirementExpression {
        public Any {
            children = List.copyOf(Objects.requireNonNull(children, "children"));
        }
    }

    record Not(RequirementExpression child) implements RequirementExpression {
        public Not {
            Objects.requireNonNull(child, "child");
        }
    }

    record SkillLevel(
            ResourceLocation skill,
            ComparisonOperator operator,
            long value,
            boolean missing
    ) implements RequirementExpression {
        public SkillLevel {
            skill = StableId.requireValid(skill);
            Objects.requireNonNull(operator, "operator");
        }

        public RequirementDependency dependency() {
            return new RequirementDependency(RequirementDependency.Kind.SKILL_LEVEL, skill);
        }
    }

    record Currency(
            ResourceLocation currency,
            ComparisonOperator operator,
            long value,
            boolean missing
    ) implements RequirementExpression {
        public Currency {
            currency = StableId.requireValid(currency);
            Objects.requireNonNull(operator, "operator");
        }

        public RequirementDependency dependency() {
            return new RequirementDependency(RequirementDependency.Kind.CURRENCY, currency);
        }
    }

    static RequirementExpression always() {
        return new All(List.of());
    }
}
