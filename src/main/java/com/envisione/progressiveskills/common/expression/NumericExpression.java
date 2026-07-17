package com.envisione.progressiveskills.common.expression;

import java.util.Objects;

public sealed interface NumericExpression permits NumericExpression.Constant, NumericExpression.Variable,
        NumericExpression.Add, NumericExpression.Subtract, NumericExpression.Multiply, NumericExpression.Divide,
        NumericExpression.Minimum, NumericExpression.Maximum, NumericExpression.Clamp, NumericExpression.Product {
    record Constant(long units) implements NumericExpression {
    }

    record Variable(ExpressionDependency dependency) implements NumericExpression {
        public Variable {
            Objects.requireNonNull(dependency, "dependency");
        }
    }

    record Add(NumericExpression left, NumericExpression right) implements NumericExpression {
        public Add {
            requirePair(left, right);
        }
    }

    record Subtract(NumericExpression left, NumericExpression right) implements NumericExpression {
        public Subtract {
            requirePair(left, right);
        }
    }

    record Multiply(NumericExpression left, NumericExpression right) implements NumericExpression {
        public Multiply {
            requirePair(left, right);
        }
    }

    record Divide(NumericExpression left, NumericExpression right) implements NumericExpression {
        public Divide {
            requirePair(left, right);
        }
    }

    record Minimum(NumericExpression left, NumericExpression right) implements NumericExpression {
        public Minimum {
            requirePair(left, right);
        }
    }

    record Maximum(NumericExpression left, NumericExpression right) implements NumericExpression {
        public Maximum {
            requirePair(left, right);
        }
    }

    record Clamp(NumericExpression value, NumericExpression minimum, NumericExpression maximum)
            implements NumericExpression {
        public Clamp {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
        }
    }

    record Product(java.util.List<NumericExpression> factors) implements NumericExpression {
        public Product {
            factors = java.util.List.copyOf(Objects.requireNonNull(factors, "factors"));
            if (factors.isEmpty()) {
                throw new IllegalArgumentException("Expression product requires at least one factor");
            }
        }
    }

    private static void requirePair(NumericExpression left, NumericExpression right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }
}
