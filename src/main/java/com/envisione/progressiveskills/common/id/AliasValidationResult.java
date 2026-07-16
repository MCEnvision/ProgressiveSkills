package com.envisione.progressiveskills.common.id;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Result of compiling a proposed alias collection into an immutable alias map. */
public record AliasValidationResult(Optional<AliasMap> aliasMap, List<AliasIssue> issues) {
    public AliasValidationResult {
        Objects.requireNonNull(aliasMap, "aliasMap");
        issues = issues.stream().sorted(Comparator.naturalOrder()).toList();
        if (issues.isEmpty() != aliasMap.isPresent()) {
            throw new IllegalArgumentException("A valid alias result has a map; an invalid result has issues");
        }
    }

    static AliasValidationResult valid(AliasMap aliasMap) {
        return new AliasValidationResult(Optional.of(aliasMap), List.of());
    }

    static AliasValidationResult invalid(List<AliasIssue> issues) {
        return new AliasValidationResult(Optional.empty(), List.copyOf(issues));
    }

    public boolean isValid() {
        return aliasMap.isPresent();
    }

    public AliasMap orThrow() {
        return aliasMap.orElseThrow(() -> new IllegalArgumentException(
                issues.stream().map(AliasIssue::message).collect(Collectors.joining("; "))
        ));
    }
}
