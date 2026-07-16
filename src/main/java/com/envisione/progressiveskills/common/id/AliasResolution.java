package com.envisione.progressiveskills.common.id;

import java.util.List;
import java.util.Objects;

/** The bounded path followed from an input definition key to its terminal replacement. */
public record AliasResolution(DefinitionKey input, DefinitionKey terminal, List<DefinitionKey> path) {
    public AliasResolution {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(terminal, "terminal");
        path = List.copyOf(path);
        if (path.isEmpty() || !path.getFirst().equals(input) || !path.getLast().equals(terminal)) {
            throw new IllegalArgumentException("Alias resolution path must begin at input and end at terminal");
        }
    }

    public int hops() {
        return path.size() - 1;
    }
}
