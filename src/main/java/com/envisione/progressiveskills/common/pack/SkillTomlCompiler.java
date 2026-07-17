package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.CurveRounding;
import com.envisione.progressiveskills.common.skill.CurveType;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCanonicalCodec;
import com.envisione.progressiveskills.common.skill.SkillCurve;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class SkillTomlCompiler {
    private static final Set<String> SKILL_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "enabled", "min_level", "max_level",
            "overflow", "negative_xp_policy", "curve", "level_currency_awards", "xp_sources", "levels", "scaling"
    );
    private static final Set<String> CURRENCY_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "minimum", "maximum", "initial", "scope"
    );
    private static final Set<String> CURVE_FIELDS = Set.of(
            "type", "rounding", "base", "step", "coefficient", "power", "factor", "custom_table"
    );
    private static final Set<String> CURRENCY_AWARD_FIELDS = Set.of(
            "id", "amount_per_level", "currency", "award_basis", "award_scope"
    );
    private static final Set<String> XP_SOURCE_FIELDS = Set.of(
            "id", "action", "key", "amount", "repeat_policy"
    );
    private static final Set<String> LEVEL_FIELDS = Set.of("id", "level", "milestone", "effects");
    private static final Set<String> EFFECT_FIELDS = Set.of(
            "id", "type", "attribute", "operation", "value"
    );
    private static final Set<String> SCALING_FIELDS = Set.of(
            "id", "type", "attribute", "operation", "per_level", "from_level", "to_level"
    );

    private SkillTomlCompiler() {
    }

    static CanonicalDefinition skill(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        TomlValues.rejectUnknown(fields, SKILL_FIELDS, key + " skill");
        int minLevel = TomlValues.optionalInteger(fields, "min_level", 0);
        int maxLevel = TomlValues.integer(fields, "max_level");
        String overflow = TomlValues.optionalString(fields, "overflow").orElse("bank");
        if (!overflow.equals("bank")) {
            throw new IllegalArgumentException("Phase 7 skills require overflow bank");
        }
        String negative = TomlValues.optionalString(fields, "negative_xp_policy").orElse("deny");
        if (!negative.equals("deny")) {
            throw new IllegalArgumentException("Phase 7 skills require negative XP policy deny");
        }
        SkillCurve curve = curve(TomlValues.object(fields, "curve", true), minLevel, maxLevel);
        SkillDefinition skill = new SkillDefinition(
                key.id(),
                presentation(fields, key + " skill"),
                TomlValues.optionalBoolean(fields, "enabled", true),
                curve,
                currencyAwards(fields),
                xpSources(fields),
                attributeGrants(fields, minLevel, maxLevel)
        );
        return SkillCanonicalCodec.encode(key, skill, provenance, sourceMap);
    }

    static CanonicalDefinition currency(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        TomlValues.rejectUnknown(fields, CURRENCY_FIELDS, key + " currency");
        CurrencyDefinition currency = new CurrencyDefinition(
                key.id(),
                presentation(fields, key + " currency"),
                longInteger(fields, "minimum", 0),
                longInteger(fields, "maximum", Long.MAX_VALUE),
                longInteger(fields, "initial", 0),
                TomlValues.optionalString(fields, "scope").orElse("character")
        );
        return SkillCanonicalCodec.encode(key, currency, provenance, sourceMap);
    }

    private static DefinitionPresentation presentation(Map<String, Object> fields, String context) {
        for (String required : List.of("display", "icon")) {
            if (!fields.containsKey(required)) {
                throw new IllegalArgumentException(context + " requires " + required);
            }
        }
        var display = TomlPresentationCompiler.component(fields.get("display"), context + ".display");
        var description = fields.containsKey("description")
                ? Optional.of(TomlPresentationCompiler.component(fields.get("description"), context + ".description"))
                : Optional.<com.envisione.progressiveskills.common.presentation.ComponentSpec>empty();
        var icon = TomlPresentationCompiler.icon(fields.get("icon"), context + ".icon");
        return new DefinitionPresentation(
                display,
                description,
                icon,
                new HashSet<>(TomlValues.stringList(fields, "search_aliases"))
        );
    }

    private static SkillCurve curve(Map<String, Object> fields, int minLevel, int maxLevel) {
        TomlValues.rejectUnknown(fields, CURVE_FIELDS, "curve");
        CurveType type = CurveType.parse(TomlValues.string(fields, "type"));
        CurveRounding rounding = CurveRounding.parse(
                TomlValues.optionalString(fields, "rounding").orElse("ceil")
        );
        return switch (type) {
            case FLAT -> SkillCurve.flat(minLevel, maxLevel, rounding, decimal(fields, "base"));
            case LINEAR -> SkillCurve.linear(
                    minLevel, maxLevel, rounding, decimal(fields, "base"), decimal(fields, "step")
            );
            case POLYNOMIAL -> SkillCurve.polynomial(
                    minLevel,
                    maxLevel,
                    rounding,
                    decimal(fields, "base"),
                    decimal(fields, "coefficient"),
                    TomlValues.integer(fields, "power")
            );
            case EXPONENTIAL -> SkillCurve.exponential(
                    minLevel, maxLevel, rounding, decimal(fields, "base"), decimal(fields, "factor")
            );
            case CUSTOM_TABLE -> SkillCurve.customTable(
                    minLevel, maxLevel, rounding, decimalList(fields, "custom_table")
            );
        };
    }

    private static List<SkillDefinition.CurrencyAward> currencyAwards(Map<String, Object> fields) {
        var result = new ArrayList<SkillDefinition.CurrencyAward>();
        for (Map<String, Object> award : objectList(fields, "level_currency_awards")) {
            TomlValues.rejectUnknown(award, CURRENCY_AWARD_FIELDS, "level currency award");
            String basis = TomlValues.optionalString(award, "award_basis").orElse("lifetime_highest_level");
            String scope = TomlValues.optionalString(award, "award_scope").orElse("character");
            if (!basis.equals("lifetime_highest_level") || !scope.equals("character")) {
                throw new IllegalArgumentException(
                        "Phase 7 level currency awards require lifetime highest level and character scope"
                );
            }
            result.add(new SkillDefinition.CurrencyAward(
                    StableId.parse(TomlValues.string(award, "id")),
                    StableId.parse(TomlValues.string(award, "currency")),
                    positiveLong(award, "amount_per_level")
            ));
        }
        return result;
    }

    private static List<SkillDefinition.CustomXpSource> xpSources(Map<String, Object> fields) {
        var result = new ArrayList<SkillDefinition.CustomXpSource>();
        for (Map<String, Object> source : objectList(fields, "xp_sources")) {
            TomlValues.rejectUnknown(source, XP_SOURCE_FIELDS, "XP source");
            String action = TomlValues.string(source, "action");
            if (!action.equals("custom")) {
                throw new IllegalArgumentException("Phase 7 XP sources support only the custom action");
            }
            result.add(new SkillDefinition.CustomXpSource(
                    StableId.parse(TomlValues.string(source, "id")),
                    StableId.parse(TomlValues.string(source, "key")),
                    positiveFixed(source, "amount"),
                    TomlValues.optionalString(source, "repeat_policy").orElse("always")
            ));
        }
        return result;
    }

    private static List<SkillDefinition.AttributeGrant> attributeGrants(
            Map<String, Object> fields,
            int minLevel,
            int maxLevel
    ) {
        var result = new ArrayList<SkillDefinition.AttributeGrant>();
        for (Map<String, Object> level : objectList(fields, "levels")) {
            TomlValues.rejectUnknown(level, LEVEL_FIELDS, "skill level");
            StableId.parse(TomlValues.string(level, "id"));
            int requiredLevel = TomlValues.integer(level, "level");
            if (requiredLevel <= minLevel || requiredLevel > maxLevel) {
                throw new IllegalArgumentException("Discrete grant level must be above the minimum and at most the cap");
            }
            TomlValues.optionalBoolean(level, "milestone", false);
            for (Map<String, Object> effect : objectList(level, "effects")) {
                result.add(attributeGrant(effect, requiredLevel));
            }
        }
        for (Map<String, Object> scaling : objectList(fields, "scaling")) {
            TomlValues.rejectUnknown(scaling, SCALING_FIELDS, "skill scaling");
            requireAttributeType(scaling);
            int from = TomlValues.optionalInteger(scaling, "from_level", minLevel + 1);
            int to = TomlValues.optionalInteger(scaling, "to_level", maxLevel);
            if (from <= minLevel || to > maxLevel) {
                throw new IllegalArgumentException("Scaling level range must be inside the skill progression range");
            }
            result.add(new SkillDefinition.AttributeGrant(
                    StableId.parse(TomlValues.string(scaling, "id")),
                    StableId.parse(TomlValues.string(scaling, "attribute")),
                    AttributeOperation.parse(TomlValues.string(scaling, "operation")),
                    nonzeroFixed(scaling, "per_level"),
                    from,
                    to,
                    true
            ));
        }
        return result;
    }

    private static SkillDefinition.AttributeGrant attributeGrant(Map<String, Object> effect, int level) {
        TomlValues.rejectUnknown(effect, EFFECT_FIELDS, "level effect");
        requireAttributeType(effect);
        return new SkillDefinition.AttributeGrant(
                StableId.parse(TomlValues.string(effect, "id")),
                StableId.parse(TomlValues.string(effect, "attribute")),
                AttributeOperation.parse(TomlValues.string(effect, "operation")),
                nonzeroFixed(effect, "value"),
                level,
                level,
                false
        );
    }

    private static void requireAttributeType(Map<String, Object> values) {
        if (!TomlValues.string(values, "type").equals("attribute")) {
            throw new IllegalArgumentException("Phase 7 skill grants support only attribute effects");
        }
    }

    private static BigDecimal decimal(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return new BigDecimal(number.toString());
    }

    private static List<BigDecimal> decimalList(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be a number list");
        }
        var result = new ArrayList<BigDecimal>();
        for (Object item : list) {
            if (!(item instanceof Number number)) {
                throw new IllegalArgumentException(key + " must contain only numbers");
            }
            result.add(new BigDecimal(number.toString()));
        }
        return result;
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

    private static long positiveLong(Map<String, Object> values, String key) {
        long value = longInteger(values, key, Long.MIN_VALUE);
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static long positiveFixed(Map<String, Object> values, String key) {
        long value = FixedPoint.fromDecimal(decimal(values, key));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static long nonzeroFixed(Map<String, Object> values, String key) {
        long value = FixedPoint.fromDecimal(decimal(values, key));
        if (value == 0) {
            throw new IllegalArgumentException(key + " must not be zero");
        }
        return value;
    }
}
