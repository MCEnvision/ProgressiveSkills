package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalIr;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Result of merge/patch, typed compilation, and alias validation. */
public record DefinitionResolution(
        CanonicalIr canonicalIr,
        Set<DefinitionKey> disabledDefinitions,
        List<PackProblem> problems
) {
    public DefinitionResolution {
        var disabled = new TreeSet<DefinitionKey>(disabledDefinitions);
        disabledDefinitions = Collections.unmodifiableSet(disabled);
        problems = problems.stream().sorted().toList();
    }

    public boolean valid() {
        return problems.isEmpty();
    }
}
