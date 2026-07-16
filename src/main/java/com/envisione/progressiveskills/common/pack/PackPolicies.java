package com.envisione.progressiveskills.common.pack;

import java.util.Locale;
import java.util.Objects;

/** Fail-closed Core policies declared by a pack manifest. */
public record PackPolicies(
        MissingRequired missingRequired,
        MissingOptional missingOptional,
        UnknownField unknownField,
        ConflictPolicy duplicateId,
        ConflictPolicy mergeConflict,
        SecretProjection secretProjection
) {
    public static final PackPolicies DEFAULT = new PackPolicies(
            MissingRequired.REJECT_PACK,
            MissingOptional.SKIP_DECLARED_BRANCH,
            UnknownField.ERROR,
            ConflictPolicy.ERROR,
            ConflictPolicy.ERROR,
            SecretProjection.REDACT
    );

    public PackPolicies {
        Objects.requireNonNull(missingRequired, "missingRequired");
        Objects.requireNonNull(missingOptional, "missingOptional");
        Objects.requireNonNull(unknownField, "unknownField");
        Objects.requireNonNull(duplicateId, "duplicateId");
        Objects.requireNonNull(mergeConflict, "mergeConflict");
        Objects.requireNonNull(secretProjection, "secretProjection");
    }

    public enum MissingRequired { REJECT_PACK }
    public enum MissingOptional { SKIP_DECLARED_BRANCH }
    public enum UnknownField { ERROR }
    public enum ConflictPolicy { ERROR }
    public enum SecretProjection { REDACT }

    public static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + type.getSimpleName() + " policy: " + value, exception);
        }
    }
}
