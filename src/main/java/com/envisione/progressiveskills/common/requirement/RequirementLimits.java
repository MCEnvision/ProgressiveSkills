package com.envisione.progressiveskills.common.requirement;

public record RequirementLimits(
        int maxNodes,
        int maxDepth,
        int maxOperations,
        int maxDependencies,
        int maxExplanations
) {
    public static final RequirementLimits CORE = new RequirementLimits(64, 16, 128, 32, 64);

    public RequirementLimits {
        if (maxNodes < 1 || maxDepth < 1 || maxOperations < 1
                || maxDependencies < 0 || maxExplanations < 1) {
            throw new IllegalArgumentException("Requirement limits must be positive and bounded");
        }
    }
}
