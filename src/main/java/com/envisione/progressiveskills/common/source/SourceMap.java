package com.envisione.progressiveskills.common.source;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable field-path to source-reference mapping. Keys are sorted so diagnostics and generated
 * artifacts are deterministic regardless of authoring-adapter map iteration order.
 */
public final class SourceMap {
    public static final int MAX_FIELD_PATH_LENGTH = 512;
    public static final int MAX_FIELD_REFERENCES = 16_384;
    private static final SourceMap EMPTY = new SourceMap(Map.of());

    private final NavigableMap<String, SourceReference> fields;

    private SourceMap(Map<String, SourceReference> fields) {
        if (fields.size() > MAX_FIELD_REFERENCES) {
            throw new IllegalArgumentException(
                    "Source map exceeds " + MAX_FIELD_REFERENCES + " field references"
            );
        }
        TreeMap<String, SourceReference> copy = new TreeMap<>();
        fields.forEach((fieldPath, reference) -> copy.put(requireFieldPath(fieldPath), Objects.requireNonNull(reference, "reference")));
        this.fields = Collections.unmodifiableNavigableMap(copy);
    }

    public static SourceMap empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public NavigableMap<String, SourceReference> fields() {
        return fields;
    }

    public Optional<SourceReference> find(String fieldPath) {
        return Optional.ofNullable(fields.get(requireFieldPath(fieldPath)));
    }

    public SourceReference require(String fieldPath) {
        String checkedPath = requireFieldPath(fieldPath);
        SourceReference reference = fields.get(checkedPath);
        if (reference == null) {
            throw new IllegalArgumentException("No source reference for field: " + checkedPath);
        }
        return reference;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    private static String requireFieldPath(String fieldPath) {
        Objects.requireNonNull(fieldPath, "fieldPath");
        if (fieldPath.isEmpty() || fieldPath.length() > MAX_FIELD_PATH_LENGTH || !fieldPath.equals(fieldPath.trim())) {
            throw new IllegalArgumentException("Field path must be nonblank, trimmed, and at most " + MAX_FIELD_PATH_LENGTH + " characters");
        }
        for (int index = 0; index < fieldPath.length();) {
            char unit = fieldPath.charAt(index);
            if (Character.isHighSurrogate(unit)
                    && (index + 1 >= fieldPath.length()
                    || !Character.isLowSurrogate(fieldPath.charAt(index + 1)))) {
                throw new IllegalArgumentException("Field path contains an unpaired high surrogate");
            }
            if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("Field path contains an unpaired low surrogate");
            }
            int codePoint = fieldPath.codePointAt(index);
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("Field path contains whitespace or a control character: " + fieldPath);
            }
            index += Character.charCount(codePoint);
        }
        return fieldPath;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SourceMap sourceMap && fields.equals(sourceMap.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    @Override
    public String toString() {
        return fields.toString();
    }

    /** Mutable staging builder; {@link #build()} always returns an isolated immutable snapshot. */
    public static final class Builder {
        private final Map<String, SourceReference> fields = new TreeMap<>();

        private Builder() {
        }

        public Builder put(String fieldPath, SourceReference reference) {
            String checkedPath = requireFieldPath(fieldPath);
            Objects.requireNonNull(reference, "reference");
            if (!fields.containsKey(checkedPath) && fields.size() >= MAX_FIELD_REFERENCES) {
                throw new IllegalArgumentException(
                        "Source map exceeds " + MAX_FIELD_REFERENCES + " field references"
                );
            }
            SourceReference previous = fields.putIfAbsent(checkedPath, reference);
            if (previous != null && !previous.equals(reference)) {
                throw new IllegalArgumentException("Conflicting source references for field: " + checkedPath);
            }
            return this;
        }

        public Builder putAll(SourceMap sourceMap) {
            Objects.requireNonNull(sourceMap, "sourceMap");
            sourceMap.fields.forEach(this::put);
            return this;
        }

        public SourceMap build() {
            return fields.isEmpty() ? EMPTY : new SourceMap(fields);
        }
    }
}
