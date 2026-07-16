package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Closed, typed value vocabulary for the generic Phase 2 canonical envelope.
 * Concrete gameplay definitions will add typed records in their own phases.
 */
public sealed interface CanonicalValue permits
        CanonicalValue.BooleanValue,
        CanonicalValue.IntegerValue,
        CanonicalValue.DecimalValue,
        CanonicalValue.TextValue,
        CanonicalValue.IdValue,
        CanonicalValue.ReferenceValue,
        CanonicalValue.ComponentValue,
        CanonicalValue.IconValue,
        CanonicalValue.ListValue,
        CanonicalValue.ObjectValue {
    int MAX_TEXT_CODE_POINTS = 32_767;
    int MAX_DECIMAL_PRECISION = 38;
    int MAX_DECIMAL_ABSOLUTE_SCALE = 18;
    int MAX_FIELD_NAME_CODE_POINTS = 128;
    int MAX_LIST_ENTRIES = 16_384;
    int MAX_OBJECT_FIELDS = 4_096;
    Pattern FIELD_NAME = Pattern.compile("[a-z][a-z0-9_]*");

    record BooleanValue(boolean value) implements CanonicalValue {
    }

    record IntegerValue(long value) implements CanonicalValue {
    }

    record DecimalValue(BigDecimal value) implements CanonicalValue {
        public DecimalValue {
            Objects.requireNonNull(value, "value");
            value = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
            if (value.precision() > MAX_DECIMAL_PRECISION) {
                throw new IllegalArgumentException(
                        "Canonical decimal exceeds precision " + MAX_DECIMAL_PRECISION
                );
            }
            if (Math.abs((long) value.scale()) > MAX_DECIMAL_ABSOLUTE_SCALE) {
                throw new IllegalArgumentException(
                        "Canonical decimal scale must be within +/-" + MAX_DECIMAL_ABSOLUTE_SCALE
                );
            }
        }
    }

    record TextValue(String value) implements CanonicalValue {
        public TextValue {
            Objects.requireNonNull(value, "value");
            if (value.codePointCount(0, value.length()) > MAX_TEXT_CODE_POINTS) {
                throw new IllegalArgumentException("Canonical text exceeds " + MAX_TEXT_CODE_POINTS + " code points");
            }
            for (int index = 0; index < value.length();) {
                char unit = value.charAt(index);
                if (Character.isHighSurrogate(unit)
                        && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))) {
                    throw new IllegalArgumentException("Canonical text contains an unpaired high surrogate");
                }
                if (Character.isLowSurrogate(unit)) {
                    throw new IllegalArgumentException("Canonical text contains an unpaired low surrogate");
                }
                int codePoint = value.codePointAt(index);
                if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                    throw new IllegalArgumentException("Canonical text contains a disallowed control character");
                }
                index += Character.charCount(codePoint);
            }
        }
    }

    record IdValue(ResourceLocation value) implements CanonicalValue {
        public IdValue {
            value = StableId.requireValid(value);
        }
    }

    record ReferenceValue(DefinitionKey value) implements CanonicalValue {
        public ReferenceValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record ComponentValue(ComponentSpec value) implements CanonicalValue {
        public ComponentValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record IconValue(IconSpec value) implements CanonicalValue {
        public IconValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record ListValue(List<CanonicalValue> values) implements CanonicalValue {
        public ListValue {
            Objects.requireNonNull(values, "values");
            if (values.size() > MAX_LIST_ENTRIES) {
                throw new IllegalArgumentException("Canonical list exceeds " + MAX_LIST_ENTRIES + " entries");
            }
            values = List.copyOf(values);
        }
    }

    record ObjectValue(Map<String, CanonicalValue> fields) implements CanonicalValue {
        public ObjectValue {
            Objects.requireNonNull(fields, "fields");
            if (fields.size() > MAX_OBJECT_FIELDS) {
                throw new IllegalArgumentException("Canonical object exceeds " + MAX_OBJECT_FIELDS + " fields");
            }
            var sorted = new TreeMap<String, CanonicalValue>();
            for (var entry : fields.entrySet()) {
                var field = Objects.requireNonNull(entry.getKey(), "field name");
                if (!FIELD_NAME.matcher(field).matches()) {
                    throw new IllegalArgumentException("Invalid canonical field name: " + field);
                }
                if (field.codePointCount(0, field.length()) > MAX_FIELD_NAME_CODE_POINTS) {
                    throw new IllegalArgumentException(
                            "Canonical field name exceeds " + MAX_FIELD_NAME_CODE_POINTS + " code points"
                    );
                }
                sorted.put(field, Objects.requireNonNull(entry.getValue(), "value for " + field));
            }
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        }
    }
}
