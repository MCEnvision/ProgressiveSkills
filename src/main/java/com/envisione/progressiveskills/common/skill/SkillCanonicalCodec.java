package com.envisione.progressiveskills.common.skill;

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

public final class SkillCanonicalCodec {
    private SkillCanonicalCodec() {
    }

    public static CanonicalDefinition encode(
            DefinitionKey key,
            SkillDefinition skill,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        if (!key.kind().equals(DefinitionKinds.SKILL) || !key.id().equals(skill.id())) {
            throw new IllegalArgumentException("Skill definition identity does not match its canonical key");
        }
        var curve = new LinkedHashMap<String, CanonicalValue>();
        curve.put("type", text(skill.curve().type().serializedName()));
        curve.put("rounding", text(skill.curve().rounding().serializedName()));
        curve.put("costs", new CanonicalValue.ListValue(
                skill.curve().costs().stream().map(CanonicalValue.IntegerValue::new)
                        .map(CanonicalValue.class::cast).toList()
        ));

        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("enabled", new CanonicalValue.BooleanValue(skill.enabled()));
        fields.put("min_level", integer(skill.curve().minLevel()));
        fields.put("max_level", integer(skill.curve().maxLevel()));
        fields.put("overflow", text("bank"));
        fields.put("curve", object(curve));
        fields.put("currency_awards", list(skill.currencyAwards().stream().map(award -> object(Map.of(
                "id", id(award.id()),
                "currency", id(award.currency()),
                "amount_per_level", integer(award.amountPerLevel())
        ))).toList()));
        fields.put("xp_sources", list(skill.xpSources().stream().map(source -> object(Map.of(
                "id", id(source.id()),
                "key", id(source.key()),
                "amount_units", integer(source.amountUnits()),
                "repeat_policy", text(source.repeatPolicy())
        ))).toList()));
        fields.put("attribute_grants", list(skill.attributeGrants().stream().map(grant -> object(Map.of(
                "id", id(grant.id()),
                "attribute", id(grant.attribute()),
                "operation", text(grant.operation().serializedName()),
                "value_units", integer(grant.valueUnits()),
                "from_level", integer(grant.fromLevel()),
                "to_level", integer(grant.toLevel()),
                "scaling", new CanonicalValue.BooleanValue(grant.scaling())
        ))).toList()));
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, skill.presentation()),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static SkillDefinition decodeSkill(CanonicalDefinition canonical) {
        requireKind(canonical, DefinitionKinds.SKILL);
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        int minLevel = Math.toIntExact(integer(fields, "min_level"));
        int maxLevel = Math.toIntExact(integer(fields, "max_level"));
        Map<String, CanonicalValue> curveFields = object(fields, "curve");
        SkillCurve curve = SkillCurve.precomputed(
                CurveType.parse(text(curveFields, "type")),
                minLevel,
                maxLevel,
                CurveRounding.parse(text(curveFields, "rounding")),
                list(curveFields, "costs").stream().map(SkillCanonicalCodec::integer).toList()
        );
        var awards = new ArrayList<SkillDefinition.CurrencyAward>();
        for (CanonicalValue value : list(fields, "currency_awards")) {
            Map<String, CanonicalValue> award = object(value);
            awards.add(new SkillDefinition.CurrencyAward(
                    id(award, "id"), id(award, "currency"), integer(award, "amount_per_level")
            ));
        }
        var xpSources = new ArrayList<SkillDefinition.CustomXpSource>();
        for (CanonicalValue value : list(fields, "xp_sources")) {
            Map<String, CanonicalValue> source = object(value);
            xpSources.add(new SkillDefinition.CustomXpSource(
                    id(source, "id"), id(source, "key"), integer(source, "amount_units"),
                    text(source, "repeat_policy")
            ));
        }
        var grants = new ArrayList<SkillDefinition.AttributeGrant>();
        for (CanonicalValue value : list(fields, "attribute_grants")) {
            Map<String, CanonicalValue> grant = object(value);
            grants.add(new SkillDefinition.AttributeGrant(
                    id(grant, "id"),
                    id(grant, "attribute"),
                    AttributeOperation.parse(text(grant, "operation")),
                    integer(grant, "value_units"),
                    Math.toIntExact(integer(grant, "from_level")),
                    Math.toIntExact(integer(grant, "to_level")),
                    bool(grant, "scaling")
            ));
        }
        return new SkillDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                bool(fields, "enabled"),
                curve,
                awards,
                xpSources,
                grants
        );
    }

    public static CanonicalDefinition encode(
            DefinitionKey key,
            CurrencyDefinition currency,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        if (!key.kind().equals(DefinitionKinds.CURRENCY) || !key.id().equals(currency.id())) {
            throw new IllegalArgumentException("Currency definition identity does not match its canonical key");
        }
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, currency.presentation()),
                object(Map.of(
                        "minimum", integer(currency.minimum()),
                        "maximum", integer(currency.maximum()),
                        "initial", integer(currency.initial()),
                        "scope", text(currency.scope())
                )),
                provenance,
                sourceMap
        );
    }

    public static CurrencyDefinition decodeCurrency(CanonicalDefinition canonical) {
        requireKind(canonical, DefinitionKinds.CURRENCY);
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        return new CurrencyDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                integer(fields, "minimum"),
                integer(fields, "maximum"),
                integer(fields, "initial"),
                text(fields, "scope")
        );
    }

    private static void requireKind(
            CanonicalDefinition canonical,
            com.envisione.progressiveskills.common.id.DefinitionKind kind
    ) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.header().key().kind().equals(kind)) {
            throw new IllegalArgumentException("Canonical definition has the wrong kind");
        }
    }

    private static CanonicalValue.IntegerValue integer(long value) {
        return new CanonicalValue.IntegerValue(value);
    }

    private static CanonicalValue.TextValue text(String value) {
        return new CanonicalValue.TextValue(value);
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
        return integer(required(parent, key));
    }

    private static long integer(CanonicalValue value) {
        if (!(value instanceof CanonicalValue.IntegerValue integer)) {
            throw new IllegalArgumentException("Expected canonical integer value");
        }
        return integer.value();
    }

    private static String text(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.TextValue text)) {
            throw new IllegalArgumentException("Expected canonical text field " + key);
        }
        return text.value();
    }

    private static ResourceLocation id(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.IdValue id)) {
            throw new IllegalArgumentException("Expected canonical id field " + key);
        }
        return id.value();
    }

    private static boolean bool(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.BooleanValue bool)) {
            throw new IllegalArgumentException("Expected canonical boolean field " + key);
        }
        return bool.value();
    }

    private static CanonicalValue required(Map<String, CanonicalValue> parent, String key) {
        CanonicalValue value = parent.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing canonical field " + key);
        }
        return value;
    }
}
