package com.envisione.progressiveskills.common.tree;

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

public final class TreeCanonicalCodec {
    private TreeCanonicalCodec() {
    }

    public static CanonicalDefinition encode(
            DefinitionKey key,
            TreeDefinition tree,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        if (!key.kind().equals(DefinitionKinds.TREE) || !key.id().equals(tree.id())) {
            throw new IllegalArgumentException("Tree definition identity does not match its canonical key");
        }
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("enabled", bool(tree.enabled()));
        fields.put("scope", text(tree.scope().serializedName()));
        tree.boundSkill().ifPresent(value -> fields.put("bind", id(value)));
        fields.put("currency", id(tree.currency()));
        fields.put("dependency_policy", text(tree.dependencyPolicy().serializedName()));
        fields.put("nodes", list(tree.nodes().stream().map(TreeCanonicalCodec::node).toList()));
        return new CanonicalDefinition(
                new DefinitionHeader(SchemaVersion.V2, key, tree.presentation()),
                object(fields),
                provenance,
                sourceMap
        );
    }

    public static TreeDefinition decode(CanonicalDefinition canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.header().key().kind().equals(DefinitionKinds.TREE)) {
            throw new IllegalArgumentException("Canonical definition has the wrong kind");
        }
        Map<String, CanonicalValue> fields = canonical.fields().fields();
        var nodes = new ArrayList<TreeNodeDefinition>();
        for (CanonicalValue value : list(fields, "nodes")) {
            nodes.add(decodeNode(object(value)));
        }
        return new TreeDefinition(
                canonical.header().key().id(),
                canonical.header().presentation().orElseThrow(),
                bool(fields, "enabled"),
                TreeScope.parse(text(fields, "scope")),
                fields.containsKey("bind") ? Optional.of(id(fields, "bind")) : Optional.empty(),
                id(fields, "currency"),
                TreeDependencyPolicy.parse(text(fields, "dependency_policy")),
                nodes
        );
    }

    private static CanonicalValue node(TreeNodeDefinition node) {
        var fields = new LinkedHashMap<String, CanonicalValue>();
        fields.put("id", id(node.id()));
        encodePresentation(fields, node.presentation());
        fields.put("cost", integer(node.cost()));
        fields.put("row", integer(node.row()));
        fields.put("column", integer(node.column()));
        fields.put("requires", ids(node.requires()));
        fields.put("requires_any", ids(node.requiresAny()));
        fields.put("minimum_skill_levels", list(node.minimumSkillLevels().entrySet().stream()
                .map(entry -> object(Map.of(
                        "skill", id(entry.getKey()),
                        "level", integer(entry.getValue())
                ))).toList()));
        fields.put("grants", list(node.grants().stream().map(grant -> object(Map.of(
                "id", id(grant.id()),
                "attribute", id(grant.attribute()),
                "operation", text(grant.operation().serializedName()),
                "value_units", integer(grant.valueUnits())
        ))).toList()));
        return object(fields);
    }

    private static TreeNodeDefinition decodeNode(Map<String, CanonicalValue> fields) {
        var minimumLevels = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (CanonicalValue value : list(fields, "minimum_skill_levels")) {
            Map<String, CanonicalValue> minimum = object(value);
            minimumLevels.put(id(minimum, "skill"), Math.toIntExact(integer(minimum, "level")));
        }
        var grants = new ArrayList<TreeAttributeGrant>();
        for (CanonicalValue value : list(fields, "grants")) {
            Map<String, CanonicalValue> grant = object(value);
            grants.add(new TreeAttributeGrant(
                    id(grant, "id"),
                    id(grant, "attribute"),
                    AttributeOperation.parse(text(grant, "operation")),
                    integer(grant, "value_units")
            ));
        }
        return new TreeNodeDefinition(
                id(fields, "id"),
                decodePresentation(fields),
                integer(fields, "cost"),
                Math.toIntExact(integer(fields, "row")),
                Math.toIntExact(integer(fields, "column")),
                idList(fields, "requires"),
                idList(fields, "requires_any"),
                minimumLevels,
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
        fields.put("search_aliases", list(presentation.searchAliases().stream().map(TreeCanonicalCodec::text).toList()));
    }

    private static DefinitionPresentation decodePresentation(Map<String, CanonicalValue> fields) {
        CanonicalValue display = required(fields, "display");
        CanonicalValue icon = required(fields, "icon");
        if (!(display instanceof CanonicalValue.ComponentValue component)) {
            throw new IllegalArgumentException("Expected canonical tree node display component");
        }
        if (!(icon instanceof CanonicalValue.IconValue iconValue)) {
            throw new IllegalArgumentException("Expected canonical tree node icon");
        }
        Optional<com.envisione.progressiveskills.common.presentation.ComponentSpec> description = Optional.empty();
        if (fields.containsKey("description")) {
            if (!(fields.get("description") instanceof CanonicalValue.ComponentValue value)) {
                throw new IllegalArgumentException("Expected canonical tree node description component");
            }
            description = Optional.of(value.value());
        }
        return new DefinitionPresentation(
                component.value(),
                description,
                iconValue.value(),
                textList(fields, "search_aliases").stream().collect(java.util.stream.Collectors.toSet())
        );
    }

    private static CanonicalValue.ListValue ids(List<ResourceLocation> values) {
        return list(values.stream().map(TreeCanonicalCodec::id).toList());
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
        CanonicalValue value = required(parent, key);
        if (!(value instanceof CanonicalValue.TextValue text)) {
            throw new IllegalArgumentException("Expected canonical text field " + key);
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
