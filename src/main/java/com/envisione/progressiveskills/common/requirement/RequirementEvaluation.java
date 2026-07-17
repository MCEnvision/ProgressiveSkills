package com.envisione.progressiveskills.common.requirement;

import java.util.List;
import java.util.Optional;

public record RequirementEvaluation(
        boolean passed,
        int operations,
        List<RequirementStep> steps,
        Optional<String> firstFailure
) {
    public RequirementEvaluation {
        steps = List.copyOf(steps);
        firstFailure = firstFailure == null ? Optional.empty() : firstFailure;
    }
}
