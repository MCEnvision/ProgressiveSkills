package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
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

public final class AbilityCanonicalCodec {
    private AbilityCanonicalCodec() {
    }

    public static CanonicalDefinition encode(
            DefinitionKey key,
            AbilityDefinition definition,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definition, "definition");
        if (!key.kind().equals(DefinitionKinds.ABILITY) || !key.id().equals(definition.id())) {
            throw new IllegalArgumentException("Ability definition identity does not match its canonical key");
        }
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("enabled", bool(definition.enabled()));
        fields.put("kind", text(definition.kind().serializedName()));
        fields.put("slot_allowed", bool(definition.slotAllowed()));
        fields.put("default_on", bool(definition.defaultOn()));
        fields.put("persistent_effects", list(definition.persistentEffects().stream()
                .map(AbilityCanonicalCodec::persistentEffect).toList()));
        fields.put("costs", list(definition.costs().stream().map(AbilityCanonicalCodec::cost).toList()));
        fields.put("targeting", targeting(definition.targeting()));
        fields.put("cooldown_group", id(definition.cooldownGroup()));
        fields.put("cooldown_ticks", integer(definition.cooldownTicks()));
        fields.put("max_charges", integer(definition.maxCharges()));
        fields.put("recharge_ticks", integer(definition.rechargeTicks()));
        fields.put("actions", list(definition.actions().stream().map(AbilityCanonicalCodec::action).toList()));
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, definition.presentation()),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static AbilityDefinition decode(CanonicalDefinition canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.header().key().kind().equals(DefinitionKinds.ABILITY)) {
            throw new IllegalArgumentException("Canonical definition has the wrong ability kind");
        }
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        var effects = new ArrayList<AbilityPersistentEffect>();
        list(fields, "persistent_effects").forEach(value -> effects.add(decodePersistentEffect(object(value))));
        var costs = new ArrayList<AbilityCost>();
        list(fields, "costs").forEach(value -> costs.add(decodeCost(object(value))));
        var actions = new ArrayList<AbilityAction>();
        list(fields, "actions").forEach(value -> actions.add(decodeAction(object(value))));
        return new AbilityDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                bool(fields, "enabled"),
                AbilityKind.parse(text(fields, "kind")),
                bool(fields, "slot_allowed"),
                bool(fields, "default_on"),
                effects,
                costs,
                decodeTargeting(object(required(fields, "targeting"))),
                id(fields, "cooldown_group"),
                Math.toIntExact(integer(fields, "cooldown_ticks")),
                Math.toIntExact(integer(fields, "max_charges")),
                Math.toIntExact(integer(fields, "recharge_ticks")),
                actions
        );
    }

    private static CanonicalValue persistentEffect(AbilityPersistentEffect effect) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(effect.id()));
        fields.put("type", text(effect.type().serializedName()));
        fields.put("target", id(effect.targetId()));
        fields.put("value", integer(effect.value()));
        if (effect instanceof AbilityAttributeEffect attribute) {
            fields.put("operation", text(attribute.operation().serializedName()));
        }
        return object(fields);
    }

    private static AbilityPersistentEffect decodePersistentEffect(Map<String, CanonicalValue> fields) {
        AbilityEffectType type = AbilityEffectType.parse(text(fields, "type"));
        if (type == AbilityEffectType.ATTRIBUTE) {
            return new AbilityAttributeEffect(
                    id(fields, "id"),
                    id(fields, "target"),
                    AttributeOperation.parse(text(fields, "operation")),
                    integer(fields, "value")
            );
        }
        long value = integer(fields, "value");
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Canonical ability flag value must be zero or one");
        }
        return new AbilityFlagEffect(id(fields, "id"), id(fields, "target"), value == 1);
    }

    private static CanonicalValue cost(AbilityCost cost) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(cost.id()));
        fields.put("type", text(cost.type().serializedName()));
        fields.put("amount", integer(cost.amount()));
        if (cost instanceof AbilityCurrencyCost currency) {
            fields.put("currency", id(currency.currency()));
        }
        return object(fields);
    }

    private static AbilityCost decodeCost(Map<String, CanonicalValue> fields) {
        AbilityCostType type = AbilityCostType.parse(text(fields, "type"));
        if (type == AbilityCostType.CURRENCY) {
            return new AbilityCurrencyCost(
                    id(fields, "id"), id(fields, "currency"), integer(fields, "amount")
            );
        }
        return new AbilityVanillaCost(id(fields, "id"), type, integer(fields, "amount"));
    }

    private static CanonicalValue targeting(AbilityTargeting targeting) {
        return object(Map.of(
                "mode", text(targeting.mode().serializedName()),
                "range", integer(targeting.range()),
                "line_of_sight", bool(targeting.lineOfSight())
        ));
    }

    private static AbilityTargeting decodeTargeting(Map<String, CanonicalValue> fields) {
        return new AbilityTargeting(
                AbilityTargetMode.parse(text(fields, "mode")),
                Math.toIntExact(integer(fields, "range")),
                bool(fields, "line_of_sight")
        );
    }

    private static CanonicalValue action(AbilityAction action) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(action.id()));
        fields.put("type", text(action.type().serializedName()));
        if (action instanceof AbilityMessageAction message) {
            fields.put("message", new CanonicalValue.ComponentValue(message.message()));
        } else if (action instanceof AbilityHealAction heal) {
            fields.put("amount", integer(heal.amountUnits()));
        } else if (action instanceof AbilityVanillaEffectAction effect) {
            fields.put("effect", id(effect.effect()));
            fields.put("amplifier", integer(effect.amplifier()));
            fields.put("duration_ticks", integer(effect.durationTicks()));
            fields.put("ambient", bool(effect.ambient()));
            fields.put("show_particles", bool(effect.showParticles()));
            fields.put("show_icon", bool(effect.showIcon()));
        }
        return object(fields);
    }

    private static AbilityAction decodeAction(Map<String, CanonicalValue> fields) {
        AbilityActionType type = AbilityActionType.parse(text(fields, "type"));
        if (type == AbilityActionType.MESSAGE) {
            CanonicalValue value = required(fields, "message");
            if (!(value instanceof CanonicalValue.ComponentValue component)) {
                throw new IllegalArgumentException("Expected canonical ability message component");
            }
            return new AbilityMessageAction(id(fields, "id"), component.value());
        }
        if (type == AbilityActionType.HEAL) {
            return new AbilityHealAction(id(fields, "id"), integer(fields, "amount"));
        }
        return new AbilityVanillaEffectAction(
                id(fields, "id"),
                id(fields, "effect"),
                Math.toIntExact(integer(fields, "amplifier")),
                Math.toIntExact(integer(fields, "duration_ticks")),
                bool(fields, "ambient"),
                bool(fields, "show_particles"),
                bool(fields, "show_icon")
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

    private static CanonicalValue required(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing canonical ability field " + key);
        }
        return value;
    }

    private static Map<String, CanonicalValue> object(CanonicalValue value) {
        if (!(value instanceof CanonicalValue.ObjectValue object)) {
            throw new IllegalArgumentException("Expected canonical ability object value");
        }
        return object.fields();
    }

    private static List<CanonicalValue> list(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.ListValue list)) {
            throw new IllegalArgumentException("Expected canonical ability list field " + key);
        }
        return list.values();
    }

    private static long integer(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.IntegerValue integer)) {
            throw new IllegalArgumentException("Expected canonical ability integer field " + key);
        }
        return integer.value();
    }

    private static String text(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.TextValue text)) {
            throw new IllegalArgumentException("Expected canonical ability text field " + key);
        }
        return text.value();
    }

    private static boolean bool(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.BooleanValue bool)) {
            throw new IllegalArgumentException("Expected canonical ability boolean field " + key);
        }
        return bool.value();
    }

    private static ResourceLocation id(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.IdValue id)) {
            throw new IllegalArgumentException("Expected canonical ability id field " + key);
        }
        return id.value();
    }
}
