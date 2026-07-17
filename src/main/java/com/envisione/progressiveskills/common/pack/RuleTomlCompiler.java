package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.rule.FakePlayerPolicy;
import com.envisione.progressiveskills.common.rule.BlockOrigin;
import com.envisione.progressiveskills.common.rule.RuleAntiExploit;
import com.envisione.progressiveskills.common.rule.RuleCanonicalCodec;
import com.envisione.progressiveskills.common.rule.RuleDefinition;
import com.envisione.progressiveskills.common.rule.RuleMatcherRegistry;
import com.envisione.progressiveskills.common.rule.RuleMatcherSpec;
import com.envisione.progressiveskills.common.rule.RuleMultiplier;
import com.envisione.progressiveskills.common.rule.RuleMultiplierMode;
import com.envisione.progressiveskills.common.rule.RuleMultiplierStage;
import com.envisione.progressiveskills.common.rule.RuleStackRule;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RuleTomlCompiler {
    private static final Set<String> RULE_FIELDS = Set.of(
            "enabled", "trigger", "priority", "stack_group", "stack_rule", "credit", "match",
            "allow_custom_name", "base", "multipliers", "anti_exploit", "outputs"
    );
    private static final Set<String> MULTIPLIER_FIELDS = Set.of(
            "id", "stage", "group", "mode", "value", "priority"
    );
    private static final Set<String> ANTI_FIELDS = Set.of(
            "fake_players", "first_time", "cooldown_ticks", "per_tick_cap", "per_minute_cap",
            "per_day_cap", "repeat_window_ticks", "repeat_decay", "minimum_multiplier",
            "allowed_block_origins"
    );
    private static final Set<String> OUTPUT_FIELDS = Set.of("id", "type", "skill", "amount_formula");

    private RuleTomlCompiler() {
    }

    static CanonicalDefinition rule(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        TomlValues.rejectUnknown(fields, RULE_FIELDS, key + " rule");
        boolean customNameAllowed = TomlValues.optionalBoolean(fields, "allow_custom_name", false);
        var matchers = TomlValues.stringList(fields, "match").stream()
                .map(value -> RuleMatcherSpec.parse(value, RuleMatcherRegistry.core(), customNameAllowed))
                .toList();
        List<Map<String, Object>> outputs = objectList(fields, "outputs");
        if (outputs.size() != 1) {
            throw new IllegalArgumentException("Phase 8 rules require exactly one XP output");
        }
        Map<String, Object> output = outputs.getFirst();
        TomlValues.rejectUnknown(output, OUTPUT_FIELDS, "rule output");
        if (!TomlValues.string(output, "type").equals("xp")) {
            throw new IllegalArgumentException("Phase 8 rule outputs support only XP");
        }
        String amountFormula = TomlValues.optionalString(output, "amount_formula").orElse("rule_amount");
        if (!amountFormula.equals("rule_amount")) {
            throw new IllegalArgumentException("Phase 8 XP output amount must use rule_amount");
        }
        RuleDefinition rule = new RuleDefinition(
                key.id(),
                TomlValues.optionalBoolean(fields, "enabled", true),
                StableId.parse(TomlValues.string(fields, "trigger")),
                TomlValues.optionalInteger(fields, "priority", 0),
                TomlValues.optionalString(fields, "stack_group").map(StableId::parse).orElse(key.id()),
                RuleStackRule.parse(TomlValues.optionalString(fields, "stack_rule").orElse("sum")),
                TomlValues.optionalString(fields, "credit").orElse("actor"),
                matchers,
                customNameAllowed,
                positiveFixed(fields, "base"),
                multipliers(fields),
                antiExploit(fields),
                new RuleDefinition.XpOutput(
                        StableId.parse(TomlValues.string(output, "id")),
                        StableId.parse(TomlValues.string(output, "skill"))
                )
        );
        return RuleCanonicalCodec.encode(key, rule, provenance, sourceMap);
    }

    private static List<RuleMultiplier> multipliers(Map<String, Object> fields) {
        var result = new ArrayList<RuleMultiplier>();
        for (Map<String, Object> multiplier : objectList(fields, "multipliers")) {
            TomlValues.rejectUnknown(multiplier, MULTIPLIER_FIELDS, "rule multiplier");
            result.add(new RuleMultiplier(
                    StableId.parse(TomlValues.string(multiplier, "id")),
                    RuleMultiplierStage.parse(TomlValues.string(multiplier, "stage")),
                    StableId.parse(TomlValues.string(multiplier, "group")),
                    RuleMultiplierMode.parse(TomlValues.string(multiplier, "mode")),
                    FixedPoint.fromDecimal(decimal(multiplier, "value")),
                    TomlValues.optionalInteger(multiplier, "priority", 0)
            ));
        }
        return result;
    }

    private static RuleAntiExploit antiExploit(Map<String, Object> fields) {
        Map<String, Object> anti = TomlValues.object(fields, "anti_exploit", false);
        TomlValues.rejectUnknown(anti, ANTI_FIELDS, "rule anti exploit");
        long repeatWindow = longInteger(anti, "repeat_window_ticks", 0);
        return new RuleAntiExploit(
                FakePlayerPolicy.parse(TomlValues.optionalString(anti, "fake_players").orElse("deny")),
                TomlValues.optionalBoolean(anti, "first_time", false),
                longInteger(anti, "cooldown_ticks", 0),
                optionalFixed(anti, "per_tick_cap", 0),
                optionalFixed(anti, "per_minute_cap", 0),
                optionalFixed(anti, "per_day_cap", 0),
                repeatWindow,
                optionalFixed(anti, "repeat_decay", FixedPoint.SCALE),
                optionalFixed(anti, "minimum_multiplier", repeatWindow == 0
                        ? FixedPoint.SCALE : 0),
                allowedBlockOrigins(anti)
        );
    }

    private static Set<BlockOrigin> allowedBlockOrigins(Map<String, Object> anti) {
        if (!anti.containsKey("allowed_block_origins")) {
            return EnumSet.of(BlockOrigin.NATURAL, BlockOrigin.CREATIVE_PLACED);
        }
        var result = EnumSet.noneOf(BlockOrigin.class);
        TomlValues.stringList(anti, "allowed_block_origins").stream()
                .map(BlockOrigin::parse)
                .forEach(result::add);
        return result;
    }

    private static BigDecimal decimal(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return new BigDecimal(number.toString());
    }

    private static long positiveFixed(Map<String, Object> values, String key) {
        long value = FixedPoint.fromDecimal(decimal(values, key));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static long optionalFixed(Map<String, Object> values, String key, long fallback) {
        return values.containsKey(key) ? FixedPoint.fromDecimal(decimal(values, key)) : fallback;
    }

    private static long longInteger(Map<String, Object> values, String key, long fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return number.longValue();
    }

    private static List<Map<String, Object>> objectList(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be an array of tables");
        }
        var result = new ArrayList<Map<String, Object>>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException(key + " must contain only tables");
            }
            @SuppressWarnings("unchecked") Map<String, Object> table = (Map<String, Object>) raw;
            result.add(table);
        }
        return result;
    }
}
