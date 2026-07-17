package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.ability.AbilityAction;
import com.envisione.progressiveskills.common.ability.AbilityActionType;
import com.envisione.progressiveskills.common.ability.AbilityAttributeEffect;
import com.envisione.progressiveskills.common.ability.AbilityCanonicalCodec;
import com.envisione.progressiveskills.common.ability.AbilityCost;
import com.envisione.progressiveskills.common.ability.AbilityCostType;
import com.envisione.progressiveskills.common.ability.AbilityCurrencyCost;
import com.envisione.progressiveskills.common.ability.AbilityDefinition;
import com.envisione.progressiveskills.common.ability.AbilityEffectType;
import com.envisione.progressiveskills.common.ability.AbilityFlagEffect;
import com.envisione.progressiveskills.common.ability.AbilityHealAction;
import com.envisione.progressiveskills.common.ability.AbilityKind;
import com.envisione.progressiveskills.common.ability.AbilityMessageAction;
import com.envisione.progressiveskills.common.ability.AbilityPersistentEffect;
import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import com.envisione.progressiveskills.common.ability.AbilityTargeting;
import com.envisione.progressiveskills.common.ability.AbilityVanillaCost;
import com.envisione.progressiveskills.common.ability.AbilityVanillaEffectAction;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AbilityTomlCompiler {
    private static final Set<String> ABILITY_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "enabled", "kind",
            "slot_allowed", "default_on", "persistent_effects", "costs", "targeting",
            "cooldown_group", "cooldown_ticks", "max_charges", "recharge_ticks", "actions"
    );
    private static final Set<String> TARGETING_FIELDS = Set.of("mode", "range", "line_of_sight");
    private static final Set<String> EFFECT_FIELDS = Set.of(
            "id", "type", "attribute", "operation", "value", "flag"
    );
    private static final Set<String> COST_FIELDS = Set.of("id", "type", "currency", "amount");
    private static final Set<String> ACTION_FIELDS = Set.of(
            "id", "type", "message", "amount", "effect", "amplifier", "duration_ticks",
            "ambient", "show_particles", "show_icon"
    );
    private static final Set<String> ADVANCED_FIELDS = Set.of(
            "triggers", "conditions", "requirements", "formula", "formulas", "scaling",
            "resources", "resource_costs", "cast_time_ticks", "channel_ticks", "on_activate",
            "effects", "keybind", "key_mapping", "commands", "action_graph", "priority",
            "stack_group", "missing_policy"
    );

    private AbilityTomlCompiler() {
    }

    static CanonicalDefinition ability(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        rejectAdvanced(fields);
        TomlValues.rejectUnknown(fields, ABILITY_FIELDS, key + " ability");
        AbilityKind kind = AbilityKind.parse(TomlValues.string(fields, "kind"));
        AbilityDefinition definition = new AbilityDefinition(
                key.id(),
                presentation(fields, key.id()),
                TomlValues.optionalBoolean(fields, "enabled", true),
                kind,
                TomlValues.optionalBoolean(fields, "slot_allowed", kind != AbilityKind.PASSIVE),
                TomlValues.optionalBoolean(fields, "default_on", false),
                persistentEffects(fields),
                costs(fields),
                targeting(fields),
                StableId.parse(TomlValues.optionalString(fields, "cooldown_group")
                        .orElse(key.id().toString())),
                optionalBoundedInteger(fields, "cooldown_ticks", 0,
                        AbilityDefinition.MAX_COOLDOWN_TICKS, 0),
                optionalBoundedInteger(fields, "max_charges", 1, AbilityDefinition.MAX_CHARGES, 1),
                optionalBoundedInteger(fields, "recharge_ticks", 0,
                        AbilityDefinition.MAX_RECHARGE_TICKS, 0),
                actions(fields)
        );
        return AbilityCanonicalCodec.encode(key, definition, provenance, sourceMap);
    }

    private static List<AbilityPersistentEffect> persistentEffects(Map<String, Object> fields) {
        var result = new ArrayList<AbilityPersistentEffect>();
        for (Map<String, Object> effect : objectList(fields, "persistent_effects")) {
            TomlValues.rejectUnknown(effect, EFFECT_FIELDS, "ability persistent effect");
            AbilityEffectType type = AbilityEffectType.parse(TomlValues.string(effect, "type"));
            ResourceLocation id = StableId.parse(TomlValues.string(effect, "id"));
            if (type == AbilityEffectType.ATTRIBUTE) {
                requireOnly(effect, Set.of("id", "type", "attribute", "operation", "value"),
                        "ability attribute effect");
                result.add(new AbilityAttributeEffect(
                        id,
                        StableId.parse(TomlValues.string(effect, "attribute")),
                        AttributeOperation.parse(TomlValues.string(effect, "operation")),
                        nonzeroFixed(effect, "value")
                ));
            } else {
                requireOnly(effect, Set.of("id", "type", "flag", "value"), "ability flag effect");
                result.add(new AbilityFlagEffect(
                        id,
                        StableId.parse(TomlValues.string(effect, "flag")),
                        requiredBoolean(effect, "value")
                ));
            }
        }
        return result;
    }

    private static List<AbilityCost> costs(Map<String, Object> fields) {
        var result = new ArrayList<AbilityCost>();
        for (Map<String, Object> cost : objectList(fields, "costs")) {
            TomlValues.rejectUnknown(cost, COST_FIELDS, "ability cost");
            AbilityCostType type = AbilityCostType.parse(TomlValues.string(cost, "type"));
            ResourceLocation id = StableId.parse(TomlValues.string(cost, "id"));
            if (type == AbilityCostType.CURRENCY) {
                requireOnly(cost, Set.of("id", "type", "currency", "amount"), "ability currency cost");
                result.add(new AbilityCurrencyCost(
                        id,
                        StableId.parse(TomlValues.string(cost, "currency")),
                        positiveLong(cost, "amount")
                ));
            } else {
                requireOnly(cost, Set.of("id", "type", "amount"), "ability vanilla cost");
                result.add(new AbilityVanillaCost(id, type, positiveLong(cost, "amount")));
            }
        }
        return result;
    }

    private static AbilityTargeting targeting(Map<String, Object> fields) {
        Map<String, Object> targeting = TomlValues.object(fields, "targeting", false);
        TomlValues.rejectUnknown(targeting, TARGETING_FIELDS, "ability targeting");
        AbilityTargetMode mode = AbilityTargetMode.parse(
                TomlValues.optionalString(targeting, "mode").orElse("self")
        );
        int range = optionalBoundedInteger(
                targeting,
                "range",
                mode == AbilityTargetMode.SELF ? 0 : 1,
                mode == AbilityTargetMode.SELF ? 0 : AbilityTargeting.MAX_RANGE,
                mode == AbilityTargetMode.SELF ? 0 : 16
        );
        return new AbilityTargeting(
                mode,
                range,
                TomlValues.optionalBoolean(targeting, "line_of_sight", mode != AbilityTargetMode.SELF)
        );
    }

    private static List<AbilityAction> actions(Map<String, Object> fields) {
        var result = new ArrayList<AbilityAction>();
        for (Map<String, Object> action : objectList(fields, "actions")) {
            TomlValues.rejectUnknown(action, ACTION_FIELDS, "ability action");
            AbilityActionType type = AbilityActionType.parse(TomlValues.string(action, "type"));
            ResourceLocation id = StableId.parse(TomlValues.string(action, "id"));
            if (type == AbilityActionType.MESSAGE) {
                requireOnly(action, Set.of("id", "type", "message"), "ability message action");
                result.add(new AbilityMessageAction(
                        id,
                        TomlPresentationCompiler.component(action.get("message"), "ability action message")
                ));
            } else if (type == AbilityActionType.HEAL) {
                requireOnly(action, Set.of("id", "type", "amount"), "ability heal action");
                result.add(new AbilityHealAction(id, positiveFixed(action, "amount")));
            } else {
                requireOnly(action, Set.of(
                        "id", "type", "effect", "amplifier", "duration_ticks",
                        "ambient", "show_particles", "show_icon"
                ), "ability vanilla effect action");
                result.add(new AbilityVanillaEffectAction(
                        id,
                        StableId.parse(TomlValues.string(action, "effect")),
                        optionalBoundedInteger(action, "amplifier", 0,
                                AbilityVanillaEffectAction.MAX_AMPLIFIER, 0),
                        boundedInteger(action, "duration_ticks", 1,
                                AbilityVanillaEffectAction.MAX_DURATION_TICKS),
                        TomlValues.optionalBoolean(action, "ambient", false),
                        TomlValues.optionalBoolean(action, "show_particles", true),
                        TomlValues.optionalBoolean(action, "show_icon", true)
                ));
            }
        }
        return result;
    }

    private static DefinitionPresentation presentation(Map<String, Object> fields, ResourceLocation id) {
        if (!fields.containsKey("display") || !fields.containsKey("icon")) {
            throw new IllegalArgumentException("Ability " + id + " requires display and icon");
        }
        ComponentSpec display = TomlPresentationCompiler.component(fields.get("display"), "ability.display");
        Optional<ComponentSpec> description = fields.containsKey("description")
                ? Optional.of(TomlPresentationCompiler.component(fields.get("description"), "ability.description"))
                : Optional.empty();
        return new DefinitionPresentation(
                display,
                description,
                TomlPresentationCompiler.icon(fields.get("icon"), "ability.icon"),
                new HashSet<>(TomlValues.stringList(fields, "search_aliases"))
        );
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
            var table = new LinkedHashMap<String, Object>();
            raw.forEach((field, entry) -> {
                if (!(field instanceof String name)) {
                    throw new IllegalArgumentException(key + " table keys must be strings");
                }
                table.put(name, entry);
            });
            result.add(table);
        }
        return result;
    }

    private static void requireOnly(Map<String, Object> fields, Set<String> supported, String context) {
        for (String field : fields.keySet()) {
            if (!supported.contains(field)) {
                throw new IllegalArgumentException(context + " does not support field " + field);
            }
        }
    }

    private static int boundedInteger(Map<String, Object> values, String key, int minimum, int maximum) {
        return optionalBoundedInteger(values, key, minimum, maximum, Integer.MIN_VALUE);
    }

    private static int optionalBoundedInteger(
            Map<String, Object> values,
            String key,
            int minimum,
            int maximum,
            int fallback
    ) {
        Object value = values.get(key);
        if (value == null && fallback != Integer.MIN_VALUE) {
            return fallback;
        }
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long result = number.longValue();
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException(key + " must be within " + minimum + " and " + maximum);
        }
        return (int) result;
    }

    private static long positiveLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long result = number.longValue();
        if (result <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return result;
    }

    private static long nonzeroFixed(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        long result = FixedPoint.fromDecimal(new BigDecimal(number.toString()));
        if (result == 0) {
            throw new IllegalArgumentException(key + " must not be zero");
        }
        return result;
    }

    private static long positiveFixed(Map<String, Object> values, String key) {
        long result = nonzeroFixed(values, key);
        if (result < 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return result;
    }

    private static boolean requiredBoolean(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return result;
    }

    private static void rejectAdvanced(Map<String, Object> fields) {
        for (String field : ADVANCED_FIELDS) {
            if (fields.containsKey(field)) {
                throw new IllegalArgumentException("Core ability schema does not support Creator field " + field);
            }
        }
    }
}
