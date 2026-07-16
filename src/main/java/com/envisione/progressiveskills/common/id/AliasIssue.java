package com.envisione.progressiveskills.common.id;

import java.util.List;
import java.util.Objects;

/** An immutable, deterministic alias-validation issue. */
public record AliasIssue(AliasIssueCode code, String message, List<DefinitionKey> relatedKeys)
        implements Comparable<AliasIssue> {
    public AliasIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Alias issue message must not be blank");
        }
        relatedKeys = List.copyOf(relatedKeys);
    }

    @Override
    public int compareTo(AliasIssue other) {
        int codeComparison = code.compareTo(other.code);
        if (codeComparison != 0) {
            return codeComparison;
        }
        int messageComparison = message.compareTo(other.message);
        if (messageComparison != 0) {
            return messageComparison;
        }
        int commonSize = Math.min(relatedKeys.size(), other.relatedKeys.size());
        for (int index = 0; index < commonSize; index++) {
            int keyComparison = relatedKeys.get(index).compareTo(other.relatedKeys.get(index));
            if (keyComparison != 0) {
                return keyComparison;
            }
        }
        return Integer.compare(relatedKeys.size(), other.relatedKeys.size());
    }
}
