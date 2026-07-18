package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierCanonicalCodec;
import com.envisione.progressiveskills.common.carrier.CarrierCurrencyAction;
import com.envisione.progressiveskills.common.carrier.CarrierDefinition;
import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierRarity;
import com.envisione.progressiveskills.common.carrier.CarrierSkillLevelAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierTreeRespecAction;
import com.envisione.progressiveskills.common.carrier.CarrierUseAction;
import com.envisione.progressiveskills.common.carrier.CarrierUseActionType;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCurve;
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

final class CarrierTomlCompiler {
    private static final Set<String> ITEM_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "enabled", "carrier",
            "behavior_version", "migration_policy", "bind", "delivery_policy", "rarity",
            "glint", "stack_size", "charges", "cooldown_ticks", "use_actions"
    );
    private static final Set<String> ACTION_FIELDS = Set.of(
            "id", "type", "skill", "currency", "tree", "amount", "consume"
    );

    private CarrierTomlCompiler() {
    }

    static CanonicalDefinition carrier(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        TomlValues.rejectUnknown(fields, ITEM_FIELDS, key + " carrier");
        CarrierMigrationPolicy migrationPolicy = CarrierMigrationPolicy.parse(
                TomlValues.optionalString(fields, "migration_policy").orElse("keep_pinned")
        );
        if (migrationPolicy == CarrierMigrationPolicy.ACCEPT_LIVE) {
            throw new IllegalArgumentException("Core carrier authoring rejects unsafe accept_live migration");
        }
        CarrierDeliveryPolicy deliveryPolicy = CarrierDeliveryPolicy.parse(
                TomlValues.optionalString(fields, "delivery_policy").orElse("pending_claim")
        );
        if (deliveryPolicy == CarrierDeliveryPolicy.PROVIDER_MAIL) {
            throw new IllegalArgumentException("Core carrier authoring requires a tested mail provider");
        }
        CarrierDefinition definition = new CarrierDefinition(
                key.id(),
                presentation(fields, key.toString()),
                TomlValues.optionalBoolean(fields, "enabled", true),
                CarrierKind.parse(TomlValues.string(fields, "carrier")),
                optionalInteger(fields, "behavior_version", 1, 1, 1_000_000),
                migrationPolicy,
                CarrierBindPolicy.parse(TomlValues.optionalString(fields, "bind").orElse("none")),
                deliveryPolicy,
                CarrierRarity.parse(TomlValues.optionalString(fields, "rarity").orElse("common")),
                TomlValues.optionalBoolean(fields, "glint", false),
                optionalInteger(fields, "stack_size", 64, 1, 64),
                optionalInteger(fields, "charges", 1, 1, 1_000_000),
                optionalInteger(fields, "cooldown_ticks", 0, 0, 72_000),
                actions(fields)
        );
        return CarrierCanonicalCodec.encode(key, definition, provenance, sourceMap);
    }

    private static List<CarrierUseAction> actions(Map<String, Object> fields) {
        var actions = new ArrayList<CarrierUseAction>();
        for (Map<String, Object> action : objectList(fields, "use_actions")) {
            TomlValues.rejectUnknown(action, ACTION_FIELDS, "carrier use action");
            ResourceLocation actionId = StableId.parse(TomlValues.string(action, "id"));
            CarrierUseActionType type = CarrierUseActionType.parse(TomlValues.string(action, "type"));
            int consume = optionalInteger(action, "consume", 1, 0, CarrierUseAction.MAX_CONSUME);
            switch (type) {
                case SKILL_XP -> {
                    requireOnly(action, Set.of("id", "type", "skill", "amount", "consume"),
                            "carrier skill XP action");
                    actions.add(new CarrierSkillXpAction(
                            actionId, StableId.parse(TomlValues.string(action, "skill")),
                            positiveFixed(action, "amount"), consume
                    ));
                }
                case SKILL_LEVEL -> {
                    requireOnly(action, Set.of("id", "type", "skill", "amount", "consume"),
                            "carrier skill level action");
                    actions.add(new CarrierSkillLevelAction(
                            actionId, StableId.parse(TomlValues.string(action, "skill")),
                            requiredInteger(action, "amount", 1, SkillCurve.MAX_LEVEL_SPAN), consume
                    ));
                }
                case CURRENCY -> {
                    requireOnly(action, Set.of("id", "type", "currency", "amount", "consume"),
                            "carrier currency action");
                    actions.add(new CarrierCurrencyAction(
                            actionId, StableId.parse(TomlValues.string(action, "currency")),
                            positiveLong(action, "amount"), consume
                    ));
                }
                case TREE_RESPEC -> {
                    requireOnly(action, Set.of("id", "type", "tree", "consume"),
                            "carrier tree respec action");
                    actions.add(new CarrierTreeRespecAction(
                            actionId, StableId.parse(TomlValues.string(action, "tree")), consume
                    ));
                }
            }
        }
        return actions;
    }

    private static DefinitionPresentation presentation(Map<String, Object> fields, String context) {
        if (!fields.containsKey("display") || !fields.containsKey("icon")) {
            throw new IllegalArgumentException(context + " carrier requires display and icon");
        }
        ComponentSpec display = TomlPresentationCompiler.component(fields.get("display"), context + ".display");
        Optional<ComponentSpec> description = fields.containsKey("description")
                ? Optional.of(TomlPresentationCompiler.component(fields.get("description"), context + ".description"))
                : Optional.empty();
        return new DefinitionPresentation(
                display,
                description,
                TomlPresentationCompiler.icon(fields.get("icon"), context + ".icon"),
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

    private static void requireOnly(Map<String, Object> fields, Set<String> allowed, String context) {
        for (String field : fields.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(context + " does not support field " + field);
            }
        }
    }

    private static int optionalInteger(
            Map<String, Object> values,
            String key,
            int fallback,
            int minimum,
            int maximum
    ) {
        if (!values.containsKey(key)) {
            return fallback;
        }
        return requiredInteger(values, key, minimum, maximum);
    }

    private static int requiredInteger(Map<String, Object> values, String key, int minimum, int maximum) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long result = number.longValue();
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException(key + " exceeds its bound");
        }
        return (int) result;
    }

    private static long positiveLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long result = number.longValue();
        if (result < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return result;
    }

    private static long positiveFixed(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        long units = FixedPoint.fromDecimal(new BigDecimal(number.toString()));
        if (units < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return units;
    }
}
