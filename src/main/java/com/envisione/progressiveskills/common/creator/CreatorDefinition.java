package com.envisione.progressiveskills.common.creator;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record CreatorDefinition(DefinitionKey key, CanonicalValue.ObjectValue fields) {
    public CreatorDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fields, "fields");
    }

    public Optional<String> text(String name) {
        CanonicalValue value = fields.fields().get(name);
        return value instanceof CanonicalValue.TextValue text ? Optional.of(text.value()) : Optional.empty();
    }

    public OptionalLong integer(String name) {
        CanonicalValue value = fields.fields().get(name);
        return value instanceof CanonicalValue.IntegerValue integer
                ? OptionalLong.of(integer.value()) : OptionalLong.empty();
    }

    public Optional<BigDecimal> decimal(String name) {
        CanonicalValue value = fields.fields().get(name);
        if (value instanceof CanonicalValue.DecimalValue decimal) {
            return Optional.of(decimal.value());
        }
        if (value instanceof CanonicalValue.IntegerValue integer) {
            return Optional.of(BigDecimal.valueOf(integer.value()));
        }
        return Optional.empty();
    }

    public boolean bool(String name, boolean fallback) {
        CanonicalValue value = fields.fields().get(name);
        return value instanceof CanonicalValue.BooleanValue bool ? bool.value() : fallback;
    }

    public Optional<ResourceLocation> id(String name) {
        return text(name).map(ResourceLocation::parse);
    }

    public List<String> texts(String name) {
        CanonicalValue value = fields.fields().get(name);
        if (!(value instanceof CanonicalValue.ListValue list)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (CanonicalValue entry : list.values()) {
            if (!(entry instanceof CanonicalValue.TextValue text)) {
                throw new IllegalArgumentException(key + " field " + name + " must contain text");
            }
            result.add(text.value());
        }
        return List.copyOf(result);
    }

    public List<ResourceLocation> ids(String name) {
        return texts(name).stream().map(ResourceLocation::parse).toList();
    }

    public Map<String, CreatorValue> object(String name) {
        CanonicalValue value = fields.fields().get(name);
        if (!(value instanceof CanonicalValue.ObjectValue object)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, CreatorValue>();
        object.fields().forEach((key, entry) -> result.put(key, new CreatorValue(entry)));
        return Collections.unmodifiableMap(result);
    }

    public List<CreatorValue> list(String name) {
        CanonicalValue value = fields.fields().get(name);
        if (!(value instanceof CanonicalValue.ListValue list)) {
            return List.of();
        }
        return list.values().stream().map(CreatorValue::new).toList();
    }

    public record CreatorValue(CanonicalValue value) {
        public CreatorValue {
            Objects.requireNonNull(value, "value");
        }

        public Optional<String> text() {
            return value instanceof CanonicalValue.TextValue text ? Optional.of(text.value()) : Optional.empty();
        }

        public OptionalLong integer() {
            return value instanceof CanonicalValue.IntegerValue integer
                    ? OptionalLong.of(integer.value()) : OptionalLong.empty();
        }

        public Optional<BigDecimal> decimal() {
            if (value instanceof CanonicalValue.DecimalValue decimal) {
                return Optional.of(decimal.value());
            }
            if (value instanceof CanonicalValue.IntegerValue integer) {
                return Optional.of(BigDecimal.valueOf(integer.value()));
            }
            return Optional.empty();
        }

        public Map<String, CreatorValue> object() {
            if (!(value instanceof CanonicalValue.ObjectValue object)) {
                return Map.of();
            }
            var result = new LinkedHashMap<String, CreatorValue>();
            object.fields().forEach((key, entry) -> result.put(key, new CreatorValue(entry)));
            return Collections.unmodifiableMap(result);
        }

        public List<CreatorValue> list() {
            if (!(value instanceof CanonicalValue.ListValue list)) {
                return List.of();
            }
            return list.values().stream().map(CreatorValue::new).toList();
        }
    }
}
