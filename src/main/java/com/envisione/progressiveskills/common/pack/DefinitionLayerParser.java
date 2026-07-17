package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.source.SourcePosition;
import com.envisione.progressiveskills.common.source.SourceReference;
import com.envisione.progressiveskills.common.source.SourceSpan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parses reserved layer metadata and path-derived identity without claiming gameplay-schema validity. */
public final class DefinitionLayerParser {
    private static final Set<String> PATCH_FIELDS = Set.of("op", "path", "value", "target_id");
    private static final Set<String> SKILL_COMPANION_FIELDS = Set.of(
            "curve", "level_currency_awards", "xp_sources", "levels", "scaling"
    );
    private static final Set<String> TREE_COMPANION_FIELDS = Set.of("nodes");

    public ParsedDefinitionLayer parse(
            PackLayer pack,
            DefinitionKind kind,
            Path file,
            TomlDocument document
    ) {
        String relative = pack.source().directory().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        var provenance = new Provenance(pack.manifest().id(), relative, "toml");
        Map<String, Object> root = document.values();
        String tableName = kind.id().getPath();
        var rootFields = new java.util.HashSet<>(
                Set.of("schema_version", "merge_intent", "expected_old_digest", "patches", tableName)
        );
        Set<String> companionFields;
        if (kind.equals(com.envisione.progressiveskills.common.id.DefinitionKinds.SKILL)) {
            companionFields = SKILL_COMPANION_FIELDS;
        } else if (kind.equals(com.envisione.progressiveskills.common.id.DefinitionKinds.TREE)) {
            companionFields = TREE_COMPANION_FIELDS;
        } else {
            companionFields = Set.of();
        }
        rootFields.addAll(companionFields);
        TomlValues.rejectUnknown(root, rootFields, "definition root");
        if (TomlValues.integer(root, "schema_version") != 2) {
            throw new IllegalArgumentException("Definition schema_version must be 2");
        }
        PackMergeIntent intent = root.containsKey("merge_intent")
                ? PackMergeIntent.parse(TomlValues.string(root, "merge_intent"))
                : PackMergeIntent.ADD;
        String kindRelativePath = relative;
        var derived = StableId.derive(kind, pack.manifest().namespace(), kindRelativePath);
        Map<String, Object> table = TomlValues.object(root, tableName,
                intent != PackMergeIntent.DISABLE && intent != PackMergeIntent.PATCH);
        if (table.containsKey("id")) {
            StableId.deriveAndMatch(kind, pack.manifest().namespace(), kindRelativePath, TomlValues.string(table, "id"));
        }
        var fields = new LinkedHashMap<>(table);
        fields.remove("id");
        for (String companion : companionFields) {
            if (root.containsKey(companion) && fields.putIfAbsent(companion, root.get(companion)) != null) {
                throw new IllegalArgumentException("Definition field collides with root table " + companion);
            }
        }
        if ((intent == PackMergeIntent.PATCH || intent == PackMergeIntent.DISABLE) && !fields.isEmpty()) {
            throw new IllegalArgumentException(intent.name().toLowerCase(java.util.Locale.ROOT)
                    + " layers cannot declare definition fields");
        }
        List<DefinitionPatch> patches = parsePatches(root, intent);
        SourceMap documentSources = document.sourceMap(provenance);
        SourceMap sourceMap = definitionSourceMap(documentSources, tableName, companionFields);
        List<SourceReference> patchSources = patchSources(documentSources, provenance, patches.size());
        return new ParsedDefinitionLayer(
                pack,
                new DefinitionKey(kind, derived),
                intent,
                TomlValues.optionalString(root, "expected_old_digest"),
                fields,
                patches,
                patchSources,
                provenance,
                sourceMap
        );
    }

    private static List<SourceReference> patchSources(
            SourceMap documentSources,
            Provenance provenance,
            int patchCount
    ) {
        var start = new SourcePosition(1, 1);
        var fallback = new SourceReference(provenance, new SourceSpan(start, start));
        var result = new ArrayList<SourceReference>(patchCount);
        for (int index = 0; index < patchCount; index++) {
            result.add(documentSources.find("patches[" + index + "].path").orElse(fallback));
        }
        return List.copyOf(result);
    }

    private static List<DefinitionPatch> parsePatches(Map<String, Object> root, PackMergeIntent intent) {
        Object value = root.get("patches");
        if (intent != PackMergeIntent.PATCH) {
            if (value != null) {
                throw new IllegalArgumentException("[[patches]] requires merge_intent = \"patch\"");
            }
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("A patch layer requires at least one [[patches]] entry");
        }
        var patches = new ArrayList<DefinitionPatch>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Each patches entry must be a table");
            }
            @SuppressWarnings("unchecked") Map<String, Object> patch = (Map<String, Object>) raw;
            TomlValues.rejectUnknown(patch, PATCH_FIELDS, "patch");
            patches.add(new DefinitionPatch(
                    PatchOperation.parse(TomlValues.string(patch, "op")),
                    TomlValues.string(patch, "path"),
                    Optional.ofNullable(patch.get("value")),
                    TomlValues.optionalString(patch, "target_id")
            ));
        }
        return List.copyOf(patches);
    }

    private static SourceMap definitionSourceMap(
            SourceMap documentMap,
            String tableName,
            Set<String> companionFields
    ) {
        var builder = SourceMap.builder();
        String prefix = tableName + ".";
        documentMap.fields().forEach((path, reference) -> {
            if (path.startsWith(prefix)) {
                String normalized = path.substring(prefix.length());
                if (!normalized.equals("id")) {
                    builder.put(normalized, reference);
                }
            } else if (companionFields.stream().anyMatch(
                    companion -> path.equals(companion) || path.startsWith(companion + ".")
                            || path.startsWith(companion + "[")
            )) {
                builder.put(path, reference);
            }
        });
        return builder.build();
    }
}
