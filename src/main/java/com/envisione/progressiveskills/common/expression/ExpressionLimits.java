package com.envisione.progressiveskills.common.expression;

public record ExpressionLimits(
        int maxNodes,
        int maxDepth,
        int maxOperations,
        int maxDependencies,
        int maxIntermediateBits
) {
    public static final ExpressionLimits CORE = new ExpressionLimits(64, 16, 128, 32, 256);

    public ExpressionLimits {
        if (maxNodes < 1 || maxDepth < 1 || maxOperations < 1
                || maxDependencies < 0 || maxIntermediateBits < 64) {
            throw new IllegalArgumentException("Expression limits must be positive and bounded");
        }
    }
}
