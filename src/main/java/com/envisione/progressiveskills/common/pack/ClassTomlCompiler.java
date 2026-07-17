package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.classdef.ClassAttributeGrant;
import com.envisione.progressiveskills.common.classdef.ClassCanonicalCodec;
import com.envisione.progressiveskills.common.classdef.ClassCurrencyCost;
import com.envisione.progressiveskills.common.classdef.ClassDefinition;
import com.envisione.progressiveskills.common.classdef.ClassEntitlementGrant;
import com.envisione.progressiveskills.common.classdef.ClassGrant;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.classdef.ClassSlotDefinition;
import com.envisione.progressiveskills.common.classdef.ClassSpellGrant;
import com.envisione.progressiveskills.common.classdef.ClassSpellLearningPolicy;
import com.envisione.progressiveskills.common.classdef.ClassStarterKit;
import com.envisione.progressiveskills.common.classdef.ClassSwapPolicy;
import com.envisione.progressiveskills.common.classdef.ClassSynergyDefinition;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
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
import java.util.TreeMap;

final class ClassTomlCompiler {
    private static final Set<String> SLOT_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "capacity", "swap_policy"
    );
    private static final Set<String> CLASS_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "enabled", "access_required",
            "slot", "slot_cost", "exclusive_tags", "prerequisites", "selection_cost",
            "respec_allowed", "respec_cost", "starter_kit", "grants", "synergy"
    );
    private static final Set<String> PREREQUISITE_FIELDS = Set.of("min_level", "nodes", "classes");
    private static final Set<String> COST_FIELDS = Set.of("currency", "amount");
    private static final Set<String> GRANT_FIELDS = Set.of(
            "id", "type", "attribute", "operation", "value", "ability", "spell", "level",
            "selection", "learning", "stage", "tree", "class"
    );
    private static final Set<String> SYNERGY_FIELDS = Set.of(
            "id", "display", "description", "icon", "search_aliases", "enabled",
            "requires_classes", "grants"
    );
    private static final Set<String> ADVANCED_CLASS_FIELDS = Set.of(
            "max_rank", "primary_allowed", "ranks", "evolution", "roles", "loadouts",
            "counts_toward_cap", "max_classes", "stackable"
    );

    private ClassTomlCompiler() {
    }

    static CanonicalDefinition slot(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        rejectAdvanced(fields, "class slot");
        TomlValues.rejectUnknown(fields, SLOT_FIELDS, key + " class slot");
        ClassSlotDefinition slot = new ClassSlotDefinition(
                key.id(),
                presentation(fields, key + " class slot", key.id(), false),
                boundedInteger(fields, "capacity", 1, ClassSlotDefinition.MAX_CAPACITY),
                ClassSwapPolicy.parse(TomlValues.optionalString(fields, "swap_policy").orElse("allowed"))
        );
        return ClassCanonicalCodec.encodeSlot(key, slot, provenance, sourceMap);
    }

    static CanonicalDefinition classDefinition(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        rejectAdvanced(fields, "class");
        TomlValues.rejectUnknown(fields, CLASS_FIELDS, key + " class");
        Map<String, Object> prerequisites = TomlValues.object(fields, "prerequisites", false);
        TomlValues.rejectUnknown(prerequisites, PREREQUISITE_FIELDS, key + " class prerequisites");
        Optional<ClassStarterKit> starterKit = starterKit(key.id(), fields);
        ClassDefinition definition = new ClassDefinition(
                key.id(),
                presentation(fields, key + " class", key.id(), true),
                TomlValues.optionalBoolean(fields, "enabled", true),
                TomlValues.optionalBoolean(fields, "access_required", false),
                StableId.parse(TomlValues.string(fields, "slot")),
                boundedInteger(fields, "slot_cost", 0, ClassDefinition.MAX_SLOT_COST),
                stableIdSet(fields, "exclusive_tags"),
                minimumLevels(prerequisites),
                stableIdList(prerequisites, "nodes"),
                stableIdList(prerequisites, "classes"),
                optionalCost(fields, "selection_cost"),
                TomlValues.optionalBoolean(fields, "respec_allowed", true),
                optionalCost(fields, "respec_cost"),
                starterKit,
                grants(fields, "class grant"),
                synergies(fields)
        );
        return ClassCanonicalCodec.encodeClass(key, definition, provenance, sourceMap);
    }

    private static Optional<ClassStarterKit> starterKit(
            ResourceLocation classId,
            Map<String, Object> fields
    ) {
        List<ResourceLocation> items = stableIdList(fields, "starter_kit");
        return items.isEmpty() ? Optional.empty() : Optional.of(new ClassStarterKit(
                ClassDefinition.starterKitReceiptId(classId), items
        ));
    }

    private static List<ClassSynergyDefinition> synergies(Map<String, Object> fields) {
        var result = new ArrayList<ClassSynergyDefinition>();
        for (Map<String, Object> synergy : objectList(fields, "synergy")) {
            TomlValues.rejectUnknown(synergy, SYNERGY_FIELDS, "class synergy");
            ResourceLocation id = StableId.parse(TomlValues.string(synergy, "id"));
            result.add(new ClassSynergyDefinition(
                    id,
                    presentation(synergy, "class synergy", id, false),
                    TomlValues.optionalBoolean(synergy, "enabled", true),
                    stableIdList(synergy, "requires_classes"),
                    grants(synergy, "class synergy grant")
            ));
        }
        return result;
    }

    private static List<ClassGrant> grants(Map<String, Object> fields, String context) {
        var result = new ArrayList<ClassGrant>();
        for (Map<String, Object> grant : objectList(fields, "grants")) {
            TomlValues.rejectUnknown(grant, GRANT_FIELDS, context);
            ClassGrantType type = ClassGrantType.parse(TomlValues.string(grant, "type"));
            ResourceLocation id = StableId.parse(TomlValues.string(grant, "id"));
            if (type == ClassGrantType.ATTRIBUTE) {
                requireOnly(grant, Set.of("id", "type", "attribute", "operation", "value"), context);
                result.add(new ClassAttributeGrant(
                        id,
                        StableId.parse(TomlValues.string(grant, "attribute")),
                        AttributeOperation.parse(TomlValues.string(grant, "operation")),
                        nonzeroFixed(grant, "value")
                ));
                continue;
            }
            Set<String> supported = type == ClassGrantType.SPELL
                    ? Set.of("id", "type", "spell", "level", "selection", "learning")
                    : Set.of("id", "type", type.targetField());
            requireOnly(grant, supported, context);
            if (type == ClassGrantType.SPELL) {
                int level = optionalBoundedInteger(grant, "level", 1, ClassSpellGrant.MAX_LEVEL, 1);
                TomlValues.optionalString(grant, "selection").ifPresent(selection -> {
                    if (!selection.equals("virtual_source")) {
                        throw new IllegalArgumentException("Core class spell grants support only virtual_source selection");
                    }
                });
                ClassSpellLearningPolicy learning = ClassSpellLearningPolicy.parse(
                        TomlValues.optionalString(grant, "learning").orElse("require_existing")
                );
                result.add(new ClassSpellGrant(
                        id,
                        StableId.parse(TomlValues.string(grant, type.targetField())),
                        level,
                        learning
                ));
                continue;
            }
            result.add(new ClassEntitlementGrant(
                    id,
                    type,
                    StableId.parse(TomlValues.string(grant, type.targetField())),
                    1
            ));
        }
        return result;
    }

    private static void requireOnly(Map<String, Object> fields, Set<String> supported, String context) {
        for (String field : fields.keySet()) {
            if (!supported.contains(field)) {
                throw new IllegalArgumentException(context + " field " + field
                        + " is not valid for grant type " + fields.get("type"));
            }
        }
    }

    private static Optional<ClassCurrencyCost> optionalCost(Map<String, Object> fields, String key) {
        if (!fields.containsKey(key)) {
            return Optional.empty();
        }
        Map<String, Object> cost = TomlValues.object(fields, key, true);
        TomlValues.rejectUnknown(cost, COST_FIELDS, key);
        return Optional.of(new ClassCurrencyCost(
                StableId.parse(TomlValues.string(cost, "currency")),
                positiveLong(cost, "amount")
        ));
    }

    private static Map<ResourceLocation, Integer> minimumLevels(Map<String, Object> prerequisites) {
        var result = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (Map.Entry<String, Object> entry : TomlValues.object(prerequisites, "min_level", false).entrySet()) {
            if (!(entry.getValue() instanceof Number number)
                    || entry.getValue() instanceof Double || entry.getValue() instanceof Float) {
                throw new IllegalArgumentException("Class min_level values must be integers");
            }
            long value = number.longValue();
            if (value < 0 || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Class min_level value exceeds its bounds");
            }
            result.put(StableId.parse(entry.getKey()), (int) value);
        }
        return result;
    }

    private static DefinitionPresentation presentation(
            Map<String, Object> fields,
            String context,
            ResourceLocation id,
            boolean required
    ) {
        if (required && (!fields.containsKey("display") || !fields.containsKey("icon"))) {
            throw new IllegalArgumentException(context + " requires display and icon");
        }
        if (!fields.containsKey("display") && !fields.containsKey("icon")) {
            return fallbackPresentation(id);
        }
        if (!fields.containsKey("display") || !fields.containsKey("icon")) {
            throw new IllegalArgumentException(context + " must declare display and icon together");
        }
        var display = TomlPresentationCompiler.component(fields.get("display"), context + ".display");
        var description = fields.containsKey("description")
                ? Optional.of(TomlPresentationCompiler.component(fields.get("description"), context + ".description"))
                : Optional.<ComponentSpec>empty();
        var icon = TomlPresentationCompiler.icon(fields.get("icon"), context + ".icon");
        return new DefinitionPresentation(
                display,
                description,
                icon,
                new HashSet<>(TomlValues.stringList(fields, "search_aliases"))
        );
    }

    private static DefinitionPresentation fallbackPresentation(ResourceLocation id) {
        ResourceLocation barrier = ResourceLocation.withDefaultNamespace("barrier");
        String fallback = id.getPath().replace('_', ' ');
        return new DefinitionPresentation(
                ComponentSpec.literal(fallback),
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, barrier, barrier, ComponentSpec.literal(fallback)),
                Set.of()
        );
    }

    private static List<ResourceLocation> stableIdList(Map<String, Object> fields, String key) {
        return TomlValues.stringList(fields, key).stream().map(StableId::parse).toList();
    }

    private static Set<ResourceLocation> stableIdSet(Map<String, Object> fields, String key) {
        List<ResourceLocation> values = stableIdList(fields, key);
        var result = new HashSet<ResourceLocation>(values);
        if (result.size() != values.size()) {
            throw new IllegalArgumentException(key + " contains duplicate ids");
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

    private static void rejectAdvanced(Map<String, Object> fields, String context) {
        for (String field : ADVANCED_CLASS_FIELDS) {
            if (fields.containsKey(field)) {
                throw new IllegalArgumentException("Core " + context
                        + " schema does not support advanced or ranked field " + field);
            }
        }
    }
}
