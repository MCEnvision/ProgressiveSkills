package com.envisione.progressiveskills.common.pack;

import java.util.List;

/** Deterministic dependency result; errors leave the ordered list empty. */
public record PackDependencyResolution(List<PackLayer> orderedPacks, List<PackProblem> problems) {
    public PackDependencyResolution {
        orderedPacks = List.copyOf(orderedPacks);
        problems = problems.stream().sorted().toList();
        if (!problems.isEmpty() && !orderedPacks.isEmpty()) {
            throw new IllegalArgumentException("Invalid dependency resolution cannot expose a partial pack order");
        }
    }

    public boolean valid() {
        return problems.isEmpty();
    }
}
