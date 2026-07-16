package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.Alias;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.source.Provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict pack-level replacements.toml adapter. */
public final class ReplacementAliasParser {
    private static final Set<String> ROOT_FIELDS = Set.of("schema_version", "replacements");
    private static final Set<String> ALIAS_FIELDS = Set.of("kind", "old_id", "new_id");

    public List<ParsedAlias> parse(PackLayer pack, TomlDocument document) {
        Map<String, Object> root = document.values();
        TomlValues.rejectUnknown(root, ROOT_FIELDS, "replacements root");
        if (TomlValues.integer(root, "schema_version") != 2) {
            throw new IllegalArgumentException("replacements.toml schema_version must be 2");
        }
        Object value = root.get("replacements");
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("replacements must be an array of tables");
        }
        var provenance = new Provenance(pack.manifest().id(), "replacements.toml", "toml");
        var aliases = new ArrayList<ParsedAlias>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Each replacement must be a table");
            }
            @SuppressWarnings("unchecked") Map<String, Object> replacement = (Map<String, Object>) raw;
            TomlValues.rejectUnknown(replacement, ALIAS_FIELDS, "replacement");
            var kind = DefinitionKinds.require(StableId.parse(TomlValues.string(replacement, "kind")));
            var oldKey = DefinitionKey.parse(kind, TomlValues.string(replacement, "old_id"));
            var newKey = DefinitionKey.parse(kind, TomlValues.string(replacement, "new_id"));
            if (!oldKey.id().getNamespace().equals(pack.manifest().namespace())
                    || !newKey.id().getNamespace().equals(pack.manifest().namespace())) {
                throw new IllegalArgumentException("Replacement ids must use the owning pack namespace "
                        + pack.manifest().namespace());
            }
            aliases.add(new ParsedAlias(new Alias(oldKey, newKey), provenance));
        }
        return aliases.stream().sorted().toList();
    }
}
