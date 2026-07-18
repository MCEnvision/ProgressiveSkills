package com.envisione.progressiveskills.common.carrier;

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

public final class CarrierCanonicalCodec {
    private CarrierCanonicalCodec() {
    }

    public static CanonicalDefinition encode(
            DefinitionKey key,
            CarrierDefinition definition,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definition, "definition");
        if (!key.kind().equals(DefinitionKinds.ITEM) || !key.id().equals(definition.id())) {
            throw new IllegalArgumentException("Carrier definition identity does not match its canonical key");
        }
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("enabled", bool(definition.enabled()));
        fields.put("carrier", text(definition.carrier().serializedName()));
        fields.put("behavior_version", integer(definition.behaviorVersion()));
        fields.put("migration_policy", text(definition.migrationPolicy().serializedName()));
        fields.put("bind", text(definition.bindPolicy().serializedName()));
        fields.put("delivery_policy", text(definition.deliveryPolicy().serializedName()));
        fields.put("rarity", text(definition.rarity().serializedName()));
        fields.put("glint", bool(definition.glint()));
        fields.put("stack_size", integer(definition.stackSize()));
        fields.put("charges", integer(definition.charges()));
        fields.put("cooldown_ticks", integer(definition.cooldownTicks()));
        fields.put("use_actions", list(definition.useActions().stream()
                .map(CarrierCanonicalCodec::action).toList()));
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, definition.presentation()),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static CarrierDefinition decode(CanonicalDefinition canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.header().key().kind().equals(DefinitionKinds.ITEM)) {
            throw new IllegalArgumentException("Canonical definition has the wrong carrier kind");
        }
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        var actions = new ArrayList<CarrierUseAction>();
        for (CanonicalValue value : list(fields, "use_actions")) {
            actions.add(decodeAction(object(value)));
        }
        return new CarrierDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                bool(fields, "enabled"),
                CarrierKind.parse(text(fields, "carrier")),
                Math.toIntExact(integer(fields, "behavior_version")),
                CarrierMigrationPolicy.parse(text(fields, "migration_policy")),
                CarrierBindPolicy.parse(text(fields, "bind")),
                CarrierDeliveryPolicy.parse(text(fields, "delivery_policy")),
                CarrierRarity.parse(text(fields, "rarity")),
                bool(fields, "glint"),
                Math.toIntExact(integer(fields, "stack_size")),
                Math.toIntExact(integer(fields, "charges")),
                Math.toIntExact(integer(fields, "cooldown_ticks")),
                actions
        );
    }

    private static CanonicalValue action(CarrierUseAction action) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(action.id()));
        fields.put("type", text(action.type().serializedName()));
        fields.put("consume", integer(action.consume()));
        if (action instanceof CarrierSkillXpAction xp) {
            fields.put("skill", id(xp.skill()));
            fields.put("amount_units", integer(xp.amountUnits()));
        } else if (action instanceof CarrierSkillLevelAction level) {
            fields.put("skill", id(level.skill()));
            fields.put("levels", integer(level.levels()));
        } else if (action instanceof CarrierCurrencyAction currency) {
            fields.put("currency", id(currency.currency()));
            fields.put("amount", integer(currency.amount()));
        } else if (action instanceof CarrierTreeRespecAction tree) {
            fields.put("tree", id(tree.tree()));
        } else {
            throw new IllegalArgumentException("Unsupported carrier action " + action.getClass().getName());
        }
        return object(fields);
    }

    private static CarrierUseAction decodeAction(Map<String, CanonicalValue> fields) {
        ResourceLocation actionId = id(fields, "id");
        CarrierUseActionType type = CarrierUseActionType.parse(text(fields, "type"));
        int consume = Math.toIntExact(integer(fields, "consume"));
        return switch (type) {
            case SKILL_XP -> new CarrierSkillXpAction(
                    actionId, id(fields, "skill"), integer(fields, "amount_units"), consume
            );
            case SKILL_LEVEL -> new CarrierSkillLevelAction(
                    actionId, id(fields, "skill"), Math.toIntExact(integer(fields, "levels")), consume
            );
            case CURRENCY -> new CarrierCurrencyAction(
                    actionId, id(fields, "currency"), integer(fields, "amount"), consume
            );
            case TREE_RESPEC -> new CarrierTreeRespecAction(actionId, id(fields, "tree"), consume);
        };
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
            throw new IllegalArgumentException("Missing canonical carrier field " + key);
        }
        return value;
    }

    private static Map<String, CanonicalValue> object(CanonicalValue value) {
        if (!(value instanceof CanonicalValue.ObjectValue object)) {
            throw new IllegalArgumentException("Expected canonical carrier object value");
        }
        return object.fields();
    }

    private static List<CanonicalValue> list(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.ListValue list)) {
            throw new IllegalArgumentException("Expected canonical carrier list field " + key);
        }
        return list.values();
    }

    private static long integer(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.IntegerValue integer)) {
            throw new IllegalArgumentException("Expected canonical carrier integer field " + key);
        }
        return integer.value();
    }

    private static String text(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.TextValue text)) {
            throw new IllegalArgumentException("Expected canonical carrier text field " + key);
        }
        return text.value();
    }

    private static boolean bool(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.BooleanValue bool)) {
            throw new IllegalArgumentException("Expected canonical carrier boolean field " + key);
        }
        return bool.value();
    }

    private static ResourceLocation id(Map<String, CanonicalValue> fields, String key) {
        CanonicalValue value = required(fields, key);
        if (!(value instanceof CanonicalValue.IdValue id)) {
            throw new IllegalArgumentException("Expected canonical carrier id field " + key);
        }
        return id.value();
    }
}
