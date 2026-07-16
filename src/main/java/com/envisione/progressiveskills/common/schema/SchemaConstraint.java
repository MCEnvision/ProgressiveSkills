package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Immutable declarative relationship between fields in one schema object. */
public record SchemaConstraint(
        SchemaConstraintKind kind,
        Optional<String> triggerField,
        List<String> fields,
        DiagnosticCode diagnosticCode,
        String description
) implements Comparable<SchemaConstraint> {
    private static final Pattern FIELD_PATH = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");

    public SchemaConstraint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(triggerField, "triggerField");
        triggerField = triggerField.map(value -> requireFieldPath(value, "triggerField"));
        fields = normalizedFields(fields);
        Objects.requireNonNull(diagnosticCode, "diagnosticCode");
        description = requireText(description, "description");

        if (kind == SchemaConstraintKind.REQUIRED_TOGETHER || kind == SchemaConstraintKind.EXACTLY_ONE) {
            if (triggerField.isPresent() || fields.size() < 2) {
                throw new IllegalArgumentException(
                        kind.metadataName() + " needs at least two fields and no trigger"
                );
            }
        } else {
            var trigger = triggerField.orElseThrow(
                    () -> new IllegalArgumentException("requires needs a trigger field")
            );
            if (fields.contains(trigger)) {
                throw new IllegalArgumentException("A requires constraint cannot require its own trigger: " + trigger);
            }
        }
    }

    public static SchemaConstraint requiredTogether(
            DiagnosticCode diagnosticCode,
            String description,
            String... fields
    ) {
        return new SchemaConstraint(
                SchemaConstraintKind.REQUIRED_TOGETHER,
                Optional.empty(),
                List.of(fields),
                diagnosticCode,
                description
        );
    }

    public static SchemaConstraint exactlyOne(
            DiagnosticCode diagnosticCode,
            String description,
            String... fields
    ) {
        return new SchemaConstraint(
                SchemaConstraintKind.EXACTLY_ONE,
                Optional.empty(),
                List.of(fields),
                diagnosticCode,
                description
        );
    }

    public static SchemaConstraint requires(
            String triggerField,
            DiagnosticCode diagnosticCode,
            String description,
            String... requiredFields
    ) {
        return new SchemaConstraint(
                SchemaConstraintKind.REQUIRES,
                Optional.of(triggerField),
                List.of(requiredFields),
                diagnosticCode,
                description
        );
    }

    @Override
    public int compareTo(SchemaConstraint other) {
        int kindComparison = kind.compareTo(other.kind);
        if (kindComparison != 0) {
            return kindComparison;
        }
        int triggerComparison = triggerField.orElse("").compareTo(other.triggerField.orElse(""));
        if (triggerComparison != 0) {
            return triggerComparison;
        }
        int fieldsComparison = String.join("\u0000", fields).compareTo(String.join("\u0000", other.fields));
        if (fieldsComparison != 0) {
            return fieldsComparison;
        }
        int diagnosticComparison = diagnosticCode.compareTo(other.diagnosticCode);
        return diagnosticComparison != 0 ? diagnosticComparison : description.compareTo(other.description);
    }

    private static List<String> normalizedFields(List<String> values) {
        Objects.requireNonNull(values, "fields");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Schema constraint must reference at least one field");
        }
        var unique = new LinkedHashSet<String>();
        for (var value : values) {
            if (!unique.add(requireFieldPath(value, "field"))) {
                throw new IllegalArgumentException("Duplicate schema constraint field: " + value);
            }
        }
        var sorted = new ArrayList<>(unique);
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    private static String requireFieldPath(String value, String name) {
        var normalized = requireText(value, name);
        if (!FIELD_PATH.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid schema constraint field path: " + normalized);
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
