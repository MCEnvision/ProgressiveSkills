package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GenericDefinitionCompiler {
    private GenericDefinitionCompiler() {
    }

    public static CanonicalDefinition compile(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        return new CanonicalDefinition(
                DefinitionHeader.withoutPresentation(SchemaVersion.V2, key),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static CanonicalValue.ObjectValue object(Map<String, ?> fields) {
        Objects.requireNonNull(fields, "fields");
        var result = new LinkedHashMap<String, CanonicalValue>();
        fields.forEach((key, value) -> result.put(key, value(value)));
        return new CanonicalValue.ObjectValue(result);
    }

    public static CanonicalValue value(Object input) {
        Objects.requireNonNull(input, "input");
        if (input instanceof Boolean value) {
            return new CanonicalValue.BooleanValue(value);
        }
        if (input instanceof Byte || input instanceof Short || input instanceof Integer || input instanceof Long) {
            return new CanonicalValue.IntegerValue(((Number) input).longValue());
        }
        if (input instanceof BigInteger value) {
            return new CanonicalValue.IntegerValue(value.longValueExact());
        }
        if (input instanceof Float || input instanceof Double || input instanceof BigDecimal) {
            return new CanonicalValue.DecimalValue(new BigDecimal(input.toString()));
        }
        if (input instanceof String value) {
            return new CanonicalValue.TextValue(value);
        }
        if (input instanceof List<?> values) {
            var result = new ArrayList<CanonicalValue>();
            values.forEach(value -> result.add(value(value)));
            return new CanonicalValue.ListValue(result);
        }
        if (input instanceof Map<?, ?> values) {
            var result = new LinkedHashMap<String, Object>();
            values.forEach((key, value) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("Generic definition object key must be text");
                }
                result.put(text, value);
            });
            return object(result);
        }
        throw new IllegalArgumentException("Unsupported generic definition value " + input.getClass().getName());
    }
}
