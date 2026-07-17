package com.envisione.progressiveskills.common.expression;

import java.util.List;

public record NumericEvaluation(
        long valueUnits,
        int operations,
        List<ExpressionDependency> dependenciesRead,
        String explanation
) {
    public NumericEvaluation {
        dependenciesRead = List.copyOf(dependenciesRead);
    }
}
