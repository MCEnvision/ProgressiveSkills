package com.envisione.progressiveskills.common.expression;

import java.util.OptionalLong;

@FunctionalInterface
public interface NumericContext {
    NumericContext EMPTY = dependency -> OptionalLong.empty();

    OptionalLong value(ExpressionDependency dependency);
}
