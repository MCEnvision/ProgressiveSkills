package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;
import java.util.regex.Pattern;

/** Definition generation and semantic digest against which a plan was built. */
public record DefinitionRevision(long generation, String semanticDigest) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public DefinitionRevision {
        if (generation < 1) {
            throw new IllegalArgumentException("Definition generation must be positive");
        }
        semanticDigest = Objects.requireNonNull(semanticDigest, "semanticDigest");
        if (!SHA_256.matcher(semanticDigest).matches()) {
            throw new IllegalArgumentException("Definition semantic digest must be lowercase SHA-256");
        }
    }
}
