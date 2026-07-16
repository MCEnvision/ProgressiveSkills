package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Complete metadata for one canonical schema field. */
public record FieldDescriptor(
        String path,
        SchemaValueType type,
        boolean required,
        List<String> allowedValues,
        Optional<SchemaDefaultValue> defaultValue,
        String description,
        String example,
        DiagnosticCode diagnosticCode,
        EditorHint editor,
        ProjectionPolicy projection,
        DiffPolicy diffPolicy,
        boolean omitWhenDefault
) implements Comparable<FieldDescriptor> {
    private static final Pattern PATH = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");

    public FieldDescriptor {
        path = requireText(path, "path");
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid schema field path: " + path);
        }
        Objects.requireNonNull(type, "type");
        allowedValues = normalizedValues(allowedValues);
        Objects.requireNonNull(defaultValue, "defaultValue");
        if (defaultValue.isPresent()) {
            var value = Objects.requireNonNull(defaultValue.orElseThrow(), "defaultValue value");
            SchemaDefaultValue.validateTree(value);
            if (!value.supports(type)) {
                throw new IllegalArgumentException(
                        "Default " + value.getClass().getSimpleName() + " is incompatible with " + type
                );
            }
            if (value instanceof SchemaDefaultValue.StringValue stringValue
                    && !allowedValues.isEmpty()
                    && !allowedValues.contains(stringValue.value())) {
                throw new IllegalArgumentException(
                        "Default value is not in the allowed values for " + path + ": " + stringValue.value()
                );
            }
        }
        description = requireText(description, "description");
        example = requireText(example, "example");
        Objects.requireNonNull(diagnosticCode, "diagnosticCode");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(diffPolicy, "diffPolicy");
        if (type == SchemaValueType.ENUM && allowedValues.isEmpty()) {
            throw new IllegalArgumentException("Enum fields must declare allowed values");
        }
        if (omitWhenDefault && defaultValue.isEmpty()) {
            throw new IllegalArgumentException("omitWhenDefault requires a declared default");
        }
    }

    public static Builder builder(String path, SchemaValueType type) {
        return new Builder(path, type);
    }

    @Override
    public int compareTo(FieldDescriptor other) {
        return path.compareTo(other.path);
    }

    private static List<String> normalizedValues(List<String> values) {
        Objects.requireNonNull(values, "allowedValues");
        var unique = new LinkedHashSet<String>();
        for (var value : values) {
            if (!unique.add(requireText(value, "allowed value"))) {
                throw new IllegalArgumentException("Duplicate allowed value: " + value);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    /** Fluent construction keeps registrations readable without weakening immutability. */
    public static final class Builder {
        private final String path;
        private final SchemaValueType type;
        private boolean required;
        private List<String> allowedValues = List.of();
        private Optional<SchemaDefaultValue> defaultValue = Optional.empty();
        private String description;
        private String example;
        private DiagnosticCode diagnosticCode;
        private EditorHint editor;
        private ProjectionPolicy projection = ProjectionPolicy.SERVER_ONLY;
        private DiffPolicy diffPolicy = DiffPolicy.REPLACE;
        private boolean omitWhenDefault;

        private Builder(String path, SchemaValueType type) {
            this.path = path;
            this.type = type;
        }

        public Builder required() {
            required = true;
            return this;
        }

        public Builder allowedValues(String... values) {
            allowedValues = List.of(values);
            return this;
        }

        public Builder defaultValue(SchemaDefaultValue value) {
            defaultValue = Optional.of(Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder defaultBoolean(boolean value) {
            return defaultValue(new SchemaDefaultValue.BooleanValue(value));
        }

        public Builder defaultInteger(long value) {
            return defaultValue(new SchemaDefaultValue.IntegerValue(value));
        }

        public Builder defaultString(String value) {
            return defaultValue(new SchemaDefaultValue.StringValue(value));
        }

        public Builder defaultEmptyList() {
            return defaultValue(new SchemaDefaultValue.ListValue(List.of()));
        }

        public Builder defaultEmptyObject() {
            return defaultValue(new SchemaDefaultValue.ObjectValue(java.util.Map.of()));
        }

        public Builder defaultFieldReference(String path) {
            defaultValue = Optional.of(new SchemaDefaultValue.FieldReference(path));
            return this;
        }

        public Builder description(String value) {
            description = value;
            return this;
        }

        public Builder example(String value) {
            example = value;
            return this;
        }

        public Builder diagnostic(DiagnosticCode value) {
            diagnosticCode = value;
            return this;
        }

        public Builder editor(EditorHint value) {
            editor = value;
            return this;
        }

        public Builder projection(ProjectionPolicy value) {
            projection = value;
            return this;
        }

        public Builder diff(DiffPolicy value) {
            diffPolicy = value;
            return this;
        }

        public Builder omitWhenDefault() {
            omitWhenDefault = true;
            return this;
        }

        public FieldDescriptor build() {
            return new FieldDescriptor(
                    path,
                    type,
                    required,
                    allowedValues,
                    defaultValue,
                    description,
                    example,
                    diagnosticCode,
                    editor,
                    projection,
                    diffPolicy,
                    omitWhenDefault
            );
        }
    }
}
