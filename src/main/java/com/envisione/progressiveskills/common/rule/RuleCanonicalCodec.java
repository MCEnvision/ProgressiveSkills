package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RuleCanonicalCodec {
    private RuleCanonicalCodec() {
    }

    public static CanonicalDefinition encode(
            DefinitionKey key,
            RuleDefinition rule,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        if (!key.kind().equals(DefinitionKinds.RULE) || !key.id().equals(rule.id())) {
            throw new IllegalArgumentException("Rule definition identity does not match its canonical key");
        }
        var anti = new LinkedHashMap<String, CanonicalValue>();
        anti.put("fake_players", text(rule.antiExploit().fakePlayers().serializedName()));
        anti.put("first_time", bool(rule.antiExploit().firstTime()));
        anti.put("cooldown_ticks", integer(rule.antiExploit().cooldownTicks()));
        anti.put("per_tick_cap_units", integer(rule.antiExploit().perTickCapUnits()));
        anti.put("per_minute_cap_units", integer(rule.antiExploit().perMinuteCapUnits()));
        anti.put("per_day_cap_units", integer(rule.antiExploit().perDayCapUnits()));
        anti.put("repeat_window_ticks", integer(rule.antiExploit().repeatWindowTicks()));
        anti.put("repeat_decay_units", integer(rule.antiExploit().repeatDecayUnits()));
        anti.put("minimum_multiplier_units", integer(rule.antiExploit().minimumMultiplierUnits()));

        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("enabled", bool(rule.enabled()));
        fields.put("trigger", id(rule.trigger()));
        fields.put("priority", integer(rule.priority()));
        fields.put("stack_group", id(rule.stackGroup()));
        fields.put("stack_rule", text(rule.stackRule().serializedName()));
        fields.put("credit", text(rule.credit()));
        fields.put("matchers", list(rule.matchers().stream()
                .map(matcher -> text(matcher.serialized())).toList()));
        fields.put("custom_name_allowed", bool(rule.customNameAllowed()));
        fields.put("base_units", integer(rule.baseUnits()));
        fields.put("multipliers", list(rule.multipliers().stream().map(multiplier -> object(Map.of(
                "id", id(multiplier.id()),
                "stage", text(multiplier.stage().serializedName()),
                "group", id(multiplier.group()),
                "mode", text(multiplier.mode().serializedName()),
                "value_units", integer(multiplier.valueUnits()),
                "priority", integer(multiplier.priority())
        ))).toList()));
        fields.put("anti_exploit", object(anti));
        fields.put("output", object(Map.of(
                "id", id(rule.output().id()),
                "type", text("xp"),
                "skill", id(rule.output().skill())
        )));
        return new CanonicalDefinition(
                DefinitionHeader.withoutPresentation(SchemaVersion.V2, key),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static RuleDefinition decode(CanonicalDefinition canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.header().key().kind().equals(DefinitionKinds.RULE)) {
            throw new IllegalArgumentException("Canonical definition has the wrong kind");
        }
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        boolean customNameAllowed = bool(fields, "custom_name_allowed");
        var matchers = new ArrayList<RuleMatcherSpec>();
        for (CanonicalValue value : list(fields, "matchers")) {
            matchers.add(RuleMatcherSpec.parse(text(value), RuleMatcherRegistry.core(), customNameAllowed));
        }
        var multipliers = new ArrayList<RuleMultiplier>();
        for (CanonicalValue value : list(fields, "multipliers")) {
            Map<String, CanonicalValue> multiplierFields = object(value);
            multipliers.add(new RuleMultiplier(
                    id(multiplierFields, "id"),
                    RuleMultiplierStage.parse(text(multiplierFields, "stage")),
                    id(multiplierFields, "group"),
                    RuleMultiplierMode.parse(text(multiplierFields, "mode")),
                    integer(multiplierFields, "value_units"),
                    Math.toIntExact(integer(multiplierFields, "priority"))
            ));
        }
        Map<String, CanonicalValue> anti = object(fields, "anti_exploit");
        Map<String, CanonicalValue> output = object(fields, "output");
        if (!text(output, "type").equals("xp")) {
            throw new IllegalArgumentException("Phase 8 rule output must be XP");
        }
        return new RuleDefinition(
                canonical.header().key().id(),
                bool(fields, "enabled"),
                id(fields, "trigger"),
                Math.toIntExact(integer(fields, "priority")),
                id(fields, "stack_group"),
                RuleStackRule.parse(text(fields, "stack_rule")),
                text(fields, "credit"),
                matchers,
                customNameAllowed,
                integer(fields, "base_units"),
                multipliers,
                new RuleAntiExploit(
                        FakePlayerPolicy.parse(text(anti, "fake_players")),
                        bool(anti, "first_time"),
                        integer(anti, "cooldown_ticks"),
                        integer(anti, "per_tick_cap_units"),
                        integer(anti, "per_minute_cap_units"),
                        integer(anti, "per_day_cap_units"),
                        integer(anti, "repeat_window_ticks"),
                        integer(anti, "repeat_decay_units"),
                        integer(anti, "minimum_multiplier_units")
                ),
                new RuleDefinition.XpOutput(id(output, "id"), id(output, "skill"))
        );
    }

    private static CanonicalValue.IntegerValue integer(long value) {
        return new CanonicalValue.IntegerValue(value);
    }

    private static CanonicalValue.TextValue text(String value) {
        return new CanonicalValue.TextValue(value);
    }

    private static CanonicalValue.BooleanValue bool(boolean value) {
        return new CanonicalValue.BooleanValue(value);
    }

    private static CanonicalValue.IdValue id(ResourceLocation value) {
        return new CanonicalValue.IdValue(value);
    }

    private static CanonicalValue.ObjectValue object(Map<String, CanonicalValue> fields) {
        return new CanonicalValue.ObjectValue(fields);
    }

    private static CanonicalValue.ListValue list(List<? extends CanonicalValue> values) {
        return new CanonicalValue.ListValue(List.copyOf(values));
    }

    private static Map<String, CanonicalValue> object(Map<String, CanonicalValue> parent, String key) {
        return object(required(parent, key));
    }

    private static Map<String, CanonicalValue> object(CanonicalValue value) {
        if (!(value instanceof CanonicalValue.ObjectValue object)) {
            throw new IllegalArgumentException("Expected canonical object value");
        }
        return object.fields();
    }

    private static List<CanonicalValue> list(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.ListValue list)) {
            throw new IllegalArgumentException("Expected canonical list field " + key);
        }
        return list.values();
    }

    private static long integer(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.IntegerValue integer)) {
            throw new IllegalArgumentException("Expected canonical integer field " + key);
        }
        return integer.value();
    }

    private static String text(Map<String, CanonicalValue> parent, String key) {
        return text(required(parent, key));
    }

    private static String text(CanonicalValue value) {
        if (!(value instanceof CanonicalValue.TextValue text)) {
            throw new IllegalArgumentException("Expected canonical text value");
        }
        return text.value();
    }

    private static boolean bool(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.BooleanValue bool)) {
            throw new IllegalArgumentException("Expected canonical boolean field " + key);
        }
        return bool.value();
    }

    private static ResourceLocation id(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.IdValue id)) {
            throw new IllegalArgumentException("Expected canonical id field " + key);
        }
        return id.value();
    }

    private static CanonicalValue required(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = parent.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing canonical field " + key);
        }
        return value;
    }
}
