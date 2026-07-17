package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.tree.TreeAttributeGrant;
import com.envisione.progressiveskills.common.tree.TreeCanonicalCodec;
import com.envisione.progressiveskills.common.tree.TreeDefinition;
import com.envisione.progressiveskills.common.tree.TreeDependencyPolicy;
import com.envisione.progressiveskills.common.tree.TreeNodeDefinition;
import com.envisione.progressiveskills.common.tree.TreeScope;
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

final class TreeTomlCompiler {
    private static final Set<String> TREE_FIELDS = Set.of(
            "display", "description", "icon", "search_aliases", "enabled", "scope", "bind",
            "currency", "dependency_policy", "nodes"
    );
    private static final Set<String> NODE_FIELDS = Set.of(
            "id", "display", "description", "icon", "search_aliases", "cost", "row", "col",
            "requires", "requires_any", "min_level", "grants"
    );
    private static final Set<String> GRANT_FIELDS = Set.of(
            "id", "type", "attribute", "operation", "value"
    );

    private TreeTomlCompiler() {
    }

    static CanonicalDefinition tree(
            DefinitionKey key,
            Map<String, Object> fields,
            Provenance provenance,
            SourceMap sourceMap
    ) {
        TomlValues.rejectUnknown(fields, TREE_FIELDS, key + " tree");
        TreeScope scope = TreeScope.parse(TomlValues.string(fields, "scope"));
        Optional<ResourceLocation> boundSkill = TomlValues.optionalString(fields, "bind").map(StableId::parse);
        TreeDefinition tree = new TreeDefinition(
                key.id(),
                presentation(fields, key + " tree"),
                TomlValues.optionalBoolean(fields, "enabled", true),
                scope,
                boundSkill,
                StableId.parse(TomlValues.string(fields, "currency")),
                TreeDependencyPolicy.parse(
                        TomlValues.optionalString(fields, "dependency_policy").orElse("cascade_refund")
                ),
                nodes(fields)
        );
        return TreeCanonicalCodec.encode(key, tree, provenance, sourceMap);
    }

    private static List<TreeNodeDefinition> nodes(Map<String, Object> fields) {
        var result = new ArrayList<TreeNodeDefinition>();
        for (Map<String, Object> node : objectList(fields, "nodes")) {
            TomlValues.rejectUnknown(node, NODE_FIELDS, "tree node");
            result.add(new TreeNodeDefinition(
                    StableId.parse(TomlValues.string(node, "id")),
                    presentation(node, "tree node"),
                    positiveLong(node, "cost"),
                    TomlValues.integer(node, "row"),
                    TomlValues.integer(node, "col"),
                    stableIdList(node, "requires"),
                    stableIdList(node, "requires_any"),
                    minimumLevels(node),
                    grants(node)
            ));
        }
        return result;
    }

    private static Map<ResourceLocation, Integer> minimumLevels(Map<String, Object> node) {
        var result = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (Map.Entry<String, Object> entry : TomlValues.object(node, "min_level", false).entrySet()) {
            if (!(entry.getValue() instanceof Number number)
                    || entry.getValue() instanceof Double || entry.getValue() instanceof Float) {
                throw new IllegalArgumentException("Tree min_level values must be integers");
            }
            long value = number.longValue();
            if (value < 0 || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Tree min_level value exceeds its bounds");
            }
            result.put(StableId.parse(entry.getKey()), (int) value);
        }
        return result;
    }

    private static List<TreeAttributeGrant> grants(Map<String, Object> node) {
        var result = new ArrayList<TreeAttributeGrant>();
        for (Map<String, Object> grant : objectList(node, "grants")) {
            TomlValues.rejectUnknown(grant, GRANT_FIELDS, "tree node grant");
            if (!TomlValues.string(grant, "type").equals("attribute")) {
                throw new IllegalArgumentException("Core tree nodes support only persistent attribute grants");
            }
            result.add(new TreeAttributeGrant(
                    StableId.parse(TomlValues.string(grant, "id")),
                    StableId.parse(TomlValues.string(grant, "attribute")),
                    AttributeOperation.parse(TomlValues.string(grant, "operation")),
                    nonzeroFixed(grant, "value")
            ));
        }
        return result;
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

    private static List<ResourceLocation> stableIdList(Map<String, Object> fields, String key) {
        return TomlValues.stringList(fields, key).stream().map(StableId::parse).toList();
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
}
