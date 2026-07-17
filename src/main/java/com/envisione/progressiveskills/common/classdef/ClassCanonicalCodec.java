package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class ClassCanonicalCodec {
    private ClassCanonicalCodec() {
    }

    public static CanonicalDefinition encodeSlot(
            DefinitionKey key,
            ClassSlotDefinition slot,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        requireIdentity(key, DefinitionKinds.CLASS_SLOT, slot.id());
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, slot.presentation()),
                object(Map.of(
                        "capacity", integer(slot.capacity()),
                        "swap_policy", text(slot.swapPolicy().serializedName())
                )),
                provenance,
                sourceMap
        );
    }

    public static ClassSlotDefinition decodeSlot(CanonicalDefinition canonical) {
        requireKind(canonical, DefinitionKinds.CLASS_SLOT);
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        return new ClassSlotDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                Math.toIntExact(integer(fields, "capacity")),
                ClassSwapPolicy.parse(text(fields, "swap_policy"))
        );
    }

    public static CanonicalDefinition encodeClass(
            DefinitionKey key,
            ClassDefinition definition,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        requireIdentity(key, DefinitionKinds.CLASS, definition.id());
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("enabled", bool(definition.enabled()));
        fields.put("access_required", bool(definition.accessRequired()));
        fields.put("slot", id(definition.slot()));
        fields.put("slot_cost", integer(definition.slotCost()));
        fields.put("exclusive_tags", ids(definition.exclusiveTags().stream().toList()));
        fields.put("minimum_skill_levels", list(definition.minimumSkillLevels().entrySet().stream()
                .map(entry -> object(Map.of(
                        "skill", id(entry.getKey()),
                        "level", integer(entry.getValue())
                ))).toList()));
        fields.put("required_nodes", ids(definition.requiredNodes()));
        fields.put("required_classes", ids(definition.requiredClasses()));
        definition.selectionCost().ifPresent(value -> fields.put("selection_cost", cost(value)));
        fields.put("respec_allowed", bool(definition.respecAllowed()));
        definition.respecCost().ifPresent(value -> fields.put("respec_cost", cost(value)));
        definition.starterKit().ifPresent(value -> fields.put("starter_kit", starterKit(value)));
        fields.put("grants", list(definition.grants().stream().map(ClassCanonicalCodec::grant).toList()));
        fields.put("synergies", list(definition.synergies().stream().map(ClassCanonicalCodec::synergy).toList()));
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, definition.presentation()),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static ClassDefinition decodeClass(CanonicalDefinition canonical) {
        requireKind(canonical, DefinitionKinds.CLASS);
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        var minimumLevels = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (CanonicalValue value : list(fields, "minimum_skill_levels")) {
            Map<String, CanonicalValue> entry = object(value);
            minimumLevels.put(id(entry, "skill"), Math.toIntExact(integer(entry, "level")));
        }
        var grants = new ArrayList<ClassGrant>();
        for (CanonicalValue value : list(fields, "grants")) {
            grants.add(decodeGrant(object(value)));
        }
        var synergies = new ArrayList<ClassSynergyDefinition>();
        for (CanonicalValue value : list(fields, "synergies")) {
            synergies.add(decodeSynergy(object(value)));
        }
        return new ClassDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                bool(fields, "enabled"),
                bool(fields, "access_required"),
                id(fields, "slot"),
                Math.toIntExact(integer(fields, "slot_cost")),
                new java.util.HashSet<>(idList(fields, "exclusive_tags")),
                minimumLevels,
                idList(fields, "required_nodes"),
                idList(fields, "required_classes"),
                fields.containsKey("selection_cost")
                        ? Optional.of(decodeCost(object(fields.get("selection_cost")))) : Optional.empty(),
                bool(fields, "respec_allowed"),
                fields.containsKey("respec_cost")
                        ? Optional.of(decodeCost(object(fields.get("respec_cost")))) : Optional.empty(),
                fields.containsKey("starter_kit")
                        ? Optional.of(decodeStarterKit(object(fields.get("starter_kit")))) : Optional.empty(),
                grants,
                synergies
        );
    }

    private static CanonicalValue cost(ClassCurrencyCost cost) {
        return object(Map.of(
                "currency", id(cost.currency()),
                "amount", integer(cost.amount())
        ));
    }

    private static ClassCurrencyCost decodeCost(Map<String, CanonicalValue> fields) {
        return new ClassCurrencyCost(id(fields, "currency"), integer(fields, "amount"));
    }

    private static CanonicalValue starterKit(ClassStarterKit starterKit) {
        return object(Map.of(
                "receipt_id", id(starterKit.receiptId()),
                "items", ids(starterKit.items())
        ));
    }

    private static ClassStarterKit decodeStarterKit(Map<String, CanonicalValue> fields) {
        return new ClassStarterKit(id(fields, "receipt_id"), idList(fields, "items"));
    }

    private static CanonicalValue grant(ClassGrant grant) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(grant.id()));
        fields.put("type", text(grant.type().serializedName()));
        fields.put("target", id(grant.targetId()));
        fields.put("value", integer(grant.value()));
        if (grant instanceof ClassAttributeGrant attribute) {
            fields.put("operation", text(attribute.operation().serializedName()));
        } else if (grant instanceof ClassSpellGrant spell) {
            fields.put("learning_policy", text(spell.learningPolicy().serializedName()));
        }
        return object(fields);
    }

    private static ClassGrant decodeGrant(Map<String, CanonicalValue> fields) {
        ClassGrantType type = ClassGrantType.parse(text(fields, "type"));
        if (type == ClassGrantType.ATTRIBUTE) {
            return new ClassAttributeGrant(
                    id(fields, "id"),
                    id(fields, "target"),
                    AttributeOperation.parse(text(fields, "operation")),
                    integer(fields, "value")
            );
        }
        if (type == ClassGrantType.SPELL) {
            return new ClassSpellGrant(
                    id(fields, "id"),
                    id(fields, "target"),
                    Math.toIntExact(integer(fields, "value")),
                    ClassSpellLearningPolicy.parse(text(fields, "learning_policy"))
            );
        }
        return new ClassEntitlementGrant(
                id(fields, "id"),
                type,
                id(fields, "target"),
                integer(fields, "value")
        );
    }

    private static CanonicalValue synergy(ClassSynergyDefinition synergy) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(synergy.id()));
        encodePresentation(fields, synergy.presentation());
        fields.put("enabled", bool(synergy.enabled()));
        fields.put("required_classes", ids(synergy.requiredClasses()));
        fields.put("grants", list(synergy.grants().stream().map(ClassCanonicalCodec::grant).toList()));
        return object(fields);
    }

    private static ClassSynergyDefinition decodeSynergy(Map<String, CanonicalValue> fields) {
        var grants = new ArrayList<ClassGrant>();
        for (CanonicalValue value : list(fields, "grants")) {
            grants.add(decodeGrant(object(value)));
        }
        return new ClassSynergyDefinition(
                id(fields, "id"),
                decodePresentation(fields),
                bool(fields, "enabled"),
                idList(fields, "required_classes"),
                grants
        );
    }

    private static void encodePresentation(
            Map<String, CanonicalValue> fields,
            DefinitionPresentation presentation
    ) {
        fields.put("display", new CanonicalValue.ComponentValue(presentation.display()));
        presentation.description().ifPresent(value -> fields.put("description", new CanonicalValue.ComponentValue(value)));
        fields.put("icon", new CanonicalValue.IconValue(presentation.icon()));
        fields.put("search_aliases", list(presentation.searchAliases().stream()
                .map(ClassCanonicalCodec::text).toList()));
    }

    private static DefinitionPresentation decodePresentation(Map<String, CanonicalValue> fields) {
        CanonicalValue display = required(fields, "display");
        CanonicalValue icon = required(fields, "icon");
        if (!(display instanceof CanonicalValue.ComponentValue component)) {
            throw new IllegalArgumentException("Expected canonical class display component");
        }
        if (!(icon instanceof CanonicalValue.IconValue iconValue)) {
            throw new IllegalArgumentException("Expected canonical class icon");
        }
        Optional<com.envisione.progressiveskills.common.presentation.ComponentSpec> description = Optional.empty();
        if (fields.containsKey("description")) {
            if (!(fields.get("description") instanceof CanonicalValue.ComponentValue value)) {
                throw new IllegalArgumentException("Expected canonical class description component");
            }
            description = Optional.of(value.value());
        }
        return new DefinitionPresentation(
                component.value(),
                description,
                iconValue.value(),
                new java.util.HashSet<>(textList(fields, "search_aliases"))
        );
    }

    private static void requireIdentity(
            DefinitionKey key,
            com.envisione.progressiveskills.common.id.DefinitionKind kind,
            ResourceLocation id
    ) {
        if (!key.kind().equals(kind) || !key.id().equals(id)) {
            throw new IllegalArgumentException("Class definition identity does not match its canonical key");
        }
    }

    private static void requireKind(
            CanonicalDefinition canonical,
            com.envisione.progressiveskills.common.id.DefinitionKind kind
    ) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.header().key().kind().equals(kind)) {
            throw new IllegalArgumentException("Canonical definition has the wrong class kind");
        }
    }

    private static CanonicalValue.ListValue ids(List<ResourceLocation> values) {
        return list(values.stream().map(ClassCanonicalCodec::id).toList());
    }

    private static List<ResourceLocation> idList(Map<String, CanonicalValue> fields, String key) {
        return list(fields, key).stream().map(value -> {
            if (!(value instanceof CanonicalValue.IdValue idValue)) {
                throw new IllegalArgumentException("Expected canonical id in " + key);
            }
            return idValue.value();
        }).toList();
    }

    private static List<String> textList(Map<String, CanonicalValue> fields, String key) {
        return list(fields, key).stream().map(value -> {
            if (!(value instanceof CanonicalValue.TextValue textValue)) {
                throw new IllegalArgumentException("Expected canonical text in " + key);
            }
            return textValue.value();
        }).toList();
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

    private static Map<String, CanonicalValue> object(CanonicalValue value) {
        if (!(value instanceof CanonicalValue.ObjectValue object)) {
            throw new IllegalArgumentException("Expected canonical class object value");
        }
        return object.fields();
    }

    private static List<CanonicalValue> list(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.ListValue list)) {
            throw new IllegalArgumentException("Expected canonical class list field " + key);
        }
        return list.values();
    }

    private static long integer(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.IntegerValue integer)) {
            throw new IllegalArgumentException("Expected canonical class integer field " + key);
        }
        return integer.value();
    }

    private static String text(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.TextValue text)) {
            throw new IllegalArgumentException("Expected canonical class text field " + key);
        }
        return text.value();
    }

    private static boolean bool(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.BooleanValue bool)) {
            throw new IllegalArgumentException("Expected canonical class boolean field " + key);
        }
        return bool.value();
    }

    private static ResourceLocation id(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.IdValue id)) {
            throw new IllegalArgumentException("Expected canonical class id field " + key);
        }
        return id.value();
    }

    private static CanonicalValue required(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = parent.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing canonical class field " + key);
        }
        return value;
    }
}
