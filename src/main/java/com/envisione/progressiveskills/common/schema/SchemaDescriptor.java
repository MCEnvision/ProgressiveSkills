package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable schema description for one authoring or internal value object. */
public record SchemaDescriptor(
        ResourceLocation id,
        int currentVersion,
        SchemaAudience audience,
        String title,
        String description,
        List<FieldDescriptor> fields,
        List<SchemaConstraint> constraints
) implements Comparable<SchemaDescriptor> {
    public SchemaDescriptor {
        id = StableId.requireValid(id);
        if (currentVersion < 1) {
            throw new IllegalArgumentException("currentVersion must be positive");
        }
        Objects.requireNonNull(audience, "audience");
        title = requireText(title, "title");
        description = requireText(description, "description");
        Objects.requireNonNull(fields, "fields");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("A schema must declare at least one field");
        }
        var sorted = new ArrayList<>(fields);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("fields must not contain null");
        }
        sorted.sort(FieldDescriptor::compareTo);
        var fieldsByPath = new LinkedHashMap<String, FieldDescriptor>();
        for (var field : sorted) {
            if (fieldsByPath.putIfAbsent(field.path(), field) != null) {
                throw new IllegalArgumentException("Duplicate schema field: " + field.path());
            }
        }
        var defaultReferences = new LinkedHashMap<String, String>();
        for (var field : sorted) {
            if (field.defaultValue().orElse(null) instanceof SchemaDefaultValue.FieldReference reference) {
                var target = fieldsByPath.get(reference.path());
                if (target == null) {
                    throw new IllegalArgumentException(
                            "Default for " + field.path() + " references unknown field " + reference.path()
                    );
                }
                if (target.type() != field.type()) {
                    throw new IllegalArgumentException(
                            "Default field reference type mismatch: " + field.path() + " -> " + reference.path()
                    );
                }
                defaultReferences.put(field.path(), reference.path());
            }
        }
        validateDefaultReferenceCycles(defaultReferences);
        fields = Collections.unmodifiableList(sorted);

        Objects.requireNonNull(constraints, "constraints");
        var sortedConstraints = new ArrayList<SchemaConstraint>(constraints.size());
        for (var constraint : constraints) {
            var checked = Objects.requireNonNull(constraint, "constraint");
            checked.triggerField().ifPresent(path -> requireConstraintField(fieldsByPath, path));
            checked.fields().forEach(path -> requireConstraintField(fieldsByPath, path));
            sortedConstraints.add(checked);
        }
        sortedConstraints.sort(SchemaConstraint::compareTo);
        if (new LinkedHashSet<>(sortedConstraints).size() != sortedConstraints.size()) {
            throw new IllegalArgumentException("Duplicate schema constraint");
        }
        constraints = Collections.unmodifiableList(sortedConstraints);
    }

    public SchemaDescriptor(
            ResourceLocation id,
            int currentVersion,
            SchemaAudience audience,
            String title,
            String description,
            List<FieldDescriptor> fields
    ) {
        this(id, currentVersion, audience, title, description, fields, List.of());
    }

    @Override
    public int compareTo(SchemaDescriptor other) {
        return id.toString().compareTo(other.id.toString());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static void validateDefaultReferenceCycles(Map<String, String> references) {
        for (var start : references.keySet()) {
            var seen = new LinkedHashSet<String>();
            var current = start;
            while (references.containsKey(current)) {
                if (!seen.add(current)) {
                    throw new IllegalArgumentException(
                            "Schema default field-reference cycle: " + String.join(" -> ", seen) + " -> " + current
                    );
                }
                current = references.get(current);
            }
        }
    }

    private static void requireConstraintField(Map<String, FieldDescriptor> fields, String path) {
        if (!fields.containsKey(path)) {
            throw new IllegalArgumentException("Schema constraint references unknown field: " + path);
        }
    }
}
