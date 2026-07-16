package com.envisione.progressiveskills.common.schema;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** A typed schema default that retains its value shape in generated metadata. */
public sealed interface SchemaDefaultValue permits
        SchemaDefaultValue.BooleanValue,
        SchemaDefaultValue.IntegerValue,
        SchemaDefaultValue.DecimalValue,
        SchemaDefaultValue.StringValue,
        SchemaDefaultValue.ListValue,
        SchemaDefaultValue.ObjectValue,
        SchemaDefaultValue.FieldReference {
    int MAX_STRING_CODE_POINTS = 4_096;
    int MAX_COLLECTION_ENTRIES = 1_024;
    int MAX_TREE_DEPTH = 32;
    int MAX_TREE_NODES = 4_096;
    int MAX_DECIMAL_PRECISION = 38;
    int MAX_DECIMAL_ABSOLUTE_SCALE = 18;
    int MAX_OBJECT_FIELD_CODE_POINTS = 128;
    Pattern FIELD_PATH = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");

    /** Compact author-facing representation used by generated reference tables. */
    String displayValue();

    /** Whether this literal can be used as the default for the declared field shape. */
    boolean supports(SchemaValueType type);

    /** Guards extension-provided metadata before recursive renderers can consume it. */
    static void validateTree(SchemaDefaultValue root) {
        Objects.requireNonNull(root, "root");
        var values = new ArrayDeque<SchemaDefaultValue>();
        var depths = new ArrayDeque<Integer>();
        values.addLast(root);
        depths.addLast(1);
        int nodes = 0;
        while (!values.isEmpty()) {
            var value = values.removeFirst();
            int depth = depths.removeFirst();
            nodes++;
            if (nodes > MAX_TREE_NODES) {
                throw new IllegalArgumentException("Schema default exceeds " + MAX_TREE_NODES + " values");
            }
            if (depth > MAX_TREE_DEPTH) {
                throw new IllegalArgumentException("Schema default exceeds depth " + MAX_TREE_DEPTH);
            }
            if (depth > 1 && value instanceof FieldReference) {
                throw new IllegalArgumentException(
                        "Schema default field references are allowed only as the complete field default"
                );
            }
            switch (value) {
                case ListValue list -> list.values().forEach(child -> {
                    values.addLast(child);
                    depths.addLast(depth + 1);
                });
                case ObjectValue object -> object.fields().values().forEach(child -> {
                    values.addLast(child);
                    depths.addLast(depth + 1);
                });
                default -> {
                    // Scalar defaults have no children.
                }
            }
        }
    }

    record BooleanValue(boolean value) implements SchemaDefaultValue {
        @Override
        public String displayValue() {
            return Boolean.toString(value);
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return type == SchemaValueType.BOOLEAN;
        }
    }

    record IntegerValue(long value) implements SchemaDefaultValue {
        @Override
        public String displayValue() {
            return Long.toString(value);
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return type == SchemaValueType.INTEGER || type == SchemaValueType.DECIMAL;
        }
    }

    record DecimalValue(BigDecimal value) implements SchemaDefaultValue {
        public DecimalValue {
            Objects.requireNonNull(value, "value");
            value = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
            if (value.precision() > MAX_DECIMAL_PRECISION) {
                throw new IllegalArgumentException(
                        "Schema default decimal exceeds precision " + MAX_DECIMAL_PRECISION
                );
            }
            if (Math.abs((long) value.scale()) > MAX_DECIMAL_ABSOLUTE_SCALE) {
                throw new IllegalArgumentException(
                        "Schema default decimal scale must be within +/-" + MAX_DECIMAL_ABSOLUTE_SCALE
                );
            }
        }

        @Override
        public String displayValue() {
            return value.toPlainString();
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return type == SchemaValueType.DECIMAL;
        }
    }

    record StringValue(String value) implements SchemaDefaultValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
            if (value.codePointCount(0, value.length()) > MAX_STRING_CODE_POINTS) {
                throw new IllegalArgumentException(
                        "Schema default string exceeds " + MAX_STRING_CODE_POINTS + " code points"
                );
            }
            for (int index = 0; index < value.length();) {
                char unit = value.charAt(index);
                if (Character.isHighSurrogate(unit)
                        && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))) {
                    throw new IllegalArgumentException("Schema default string contains an unpaired high surrogate");
                }
                if (Character.isLowSurrogate(unit)) {
                    throw new IllegalArgumentException("Schema default string contains an unpaired low surrogate");
                }
                int codePoint = value.codePointAt(index);
                if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                    throw new IllegalArgumentException("Schema default string contains a disallowed control character");
                }
                index += Character.charCount(codePoint);
            }
        }

        @Override
        public String displayValue() {
            return '"' + value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t") + '"';
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return switch (type) {
                case STRING, ENUM, RESOURCE_LOCATION, REFERENCE -> true;
                default -> false;
            };
        }
    }

    record ListValue(List<SchemaDefaultValue> values) implements SchemaDefaultValue {
        public ListValue {
            Objects.requireNonNull(values, "values");
            if (values.size() > MAX_COLLECTION_ENTRIES) {
                throw new IllegalArgumentException(
                        "Schema default list exceeds " + MAX_COLLECTION_ENTRIES + " entries"
                );
            }
            values = List.copyOf(values);
        }

        @Override
        public String displayValue() {
            return values.stream()
                    .map(SchemaDefaultValue::displayValue)
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return type == SchemaValueType.LIST;
        }
    }

    record ObjectValue(Map<String, SchemaDefaultValue> fields) implements SchemaDefaultValue {
        public ObjectValue {
            Objects.requireNonNull(fields, "fields");
            if (fields.size() > MAX_COLLECTION_ENTRIES) {
                throw new IllegalArgumentException(
                        "Schema default object exceeds " + MAX_COLLECTION_ENTRIES + " fields"
                );
            }
            var sorted = new TreeMap<String, SchemaDefaultValue>();
            for (var entry : fields.entrySet()) {
                var key = Objects.requireNonNull(entry.getKey(), "field name");
                if (!FIELD_PATH.matcher(key).matches()) {
                    throw new IllegalArgumentException("Invalid default object field: " + key);
                }
                if (key.codePointCount(0, key.length()) > MAX_OBJECT_FIELD_CODE_POINTS) {
                    throw new IllegalArgumentException(
                            "Schema default object field exceeds " + MAX_OBJECT_FIELD_CODE_POINTS + " code points"
                    );
                }
                sorted.put(key, Objects.requireNonNull(entry.getValue(), "value for " + key));
            }
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }

        @Override
        public String displayValue() {
            return fields.entrySet().stream()
                    .map(entry -> entry.getKey() + " = " + entry.getValue().displayValue())
                    .collect(java.util.stream.Collectors.joining(", ", "{", "}"));
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return type == SchemaValueType.OBJECT || type == SchemaValueType.MAP;
        }
    }

    /** A default inherited from another field in the same schema object. */
    record FieldReference(String path) implements SchemaDefaultValue {
        public FieldReference {
            Objects.requireNonNull(path, "path");
            path = path.strip();
            if (!FIELD_PATH.matcher(path).matches()) {
                throw new IllegalArgumentException("Invalid default field reference: " + path);
            }
        }

        @Override
        public String displayValue() {
            return "field(" + path + ")";
        }

        @Override
        public boolean supports(SchemaValueType type) {
            return true;
        }
    }
}
