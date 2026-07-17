package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

public record RuleMatcherSpec(String prefix, String value, boolean negated) {
    public static final int MAX_VALUE_LENGTH = 256;

    public RuleMatcherSpec {
        prefix = Objects.requireNonNull(prefix, "prefix");
        value = Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > MAX_VALUE_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Rule matcher value must be bounded printable text");
        }
    }

    public static RuleMatcherSpec parse(
            String authored,
            RuleMatcherRegistry registry,
            boolean customNameAllowed
    ) {
        String value = Objects.requireNonNull(authored, "authored").strip();
        boolean negated = value.startsWith("!");
        String body = negated ? value.substring(1) : value;
        int separator = body.indexOf(':');
        String authoredPrefix = separator < 1 ? "" : body.substring(0, separator);
        boolean explicitPrefix = registry.find(authoredPrefix).isPresent();
        String prefix = explicitPrefix ? authoredPrefix : "id";
        String matcherValue = explicitPrefix ? body.substring(separator + 1) : body;
        if (matcherValue.isEmpty()) {
            throw new IllegalArgumentException("Rule matcher requires a value " + authored);
        }
        if (prefix.equals("id") || prefix.equals("tag") || prefix.equals("school")) {
            StableId.parse(matcherValue);
        } else if (prefix.equals("mod")) {
            StableId.requireNamespace(matcherValue);
        } else if (prefix.equals("custom_name")) {
            if (!customNameAllowed) {
                throw new IllegalArgumentException("custom_name matching requires explicit opt in");
            }
            matcherValue = matcherValue.strip().toLowerCase(Locale.ROOT);
        }
        return new RuleMatcherSpec(prefix, matcherValue, negated);
    }

    public ResourceLocation idValue() {
        if (!prefix.equals("id") && !prefix.equals("tag") && !prefix.equals("school")) {
            throw new IllegalStateException("Rule matcher does not contain a stable id");
        }
        return StableId.parse(value);
    }

    public String serialized() {
        return (negated ? "!" : "") + prefix + ":" + value;
    }
}
