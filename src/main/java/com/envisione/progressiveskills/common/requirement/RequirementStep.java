package com.envisione.progressiveskills.common.requirement;

public record RequirementStep(
        RequirementDependency dependency,
        boolean present,
        long actual,
        ComparisonOperator operator,
        long expected,
        boolean passed,
        String explanation
) {
}
