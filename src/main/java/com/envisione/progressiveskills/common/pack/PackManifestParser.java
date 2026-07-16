package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.source.Provenance;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Strict schema-v2 pack.toml adapter. */
public final class PackManifestParser {
    private static final Set<String> ROOT_FIELDS = Set.of("schema_version", "pack", "dependencies", "policies");
    private static final Set<String> PACK_FIELDS = Set.of(
            "id", "namespace", "name", "content_version", "engine", "authors", "license", "priority",
            "default_locale", "default_theme", "default_layout", "homepage", "source", "description",
            "changelog_url", "feature_flags", "exported_asset_pack_id", "trusted_scripts"
    );
    private static final Set<String> DEPENDENCY_FIELDS = Set.of(
            "required_packs", "optional_packs", "required_mods", "optional_mods", "incompatible_mods"
    );
    private static final Set<String> POLICY_FIELDS = Set.of(
            "missing_required", "missing_optional", "unknown_field", "duplicate_id", "merge_conflict",
            "secret_projection"
    );

    public PackLayer parse(PackSource source, TomlDocument document) {
        Map<String, Object> root = document.values();
        TomlValues.rejectUnknown(root, ROOT_FIELDS, "pack manifest");
        int schemaVersion = TomlValues.integer(root, "schema_version");
        Map<String, Object> pack = TomlValues.object(root, "pack", true);
        Map<String, Object> dependencies = TomlValues.object(root, "dependencies", false);
        Map<String, Object> policies = TomlValues.object(root, "policies", false);
        TomlValues.rejectUnknown(pack, PACK_FIELDS, "[pack]");
        TomlValues.rejectUnknown(dependencies, DEPENDENCY_FIELDS, "[dependencies]");
        TomlValues.rejectUnknown(policies, POLICY_FIELDS, "[policies]");

        ResourceLocation id = StableId.parse(TomlValues.string(pack, "id"));
        String namespace = TomlValues.string(pack, "namespace");
        var manifest = new PackManifest(
                schemaVersion,
                id,
                namespace,
                TomlPresentationCompiler.component(pack.get("name"), "pack.name"),
                SemanticVersion.parse(TomlValues.string(pack, "content_version")),
                VersionConstraint.parse(TomlValues.string(pack, "engine")),
                TomlValues.stringList(pack, "authors"),
                TomlValues.string(pack, "license"),
                TomlValues.optionalInteger(pack, "priority", 0),
                TomlValues.optionalString(pack, "default_locale").orElse("en_us"),
                optionalId(pack, "default_theme"),
                optionalId(pack, "default_layout"),
                requirements(dependencies, "required_packs"),
                requirements(dependencies, "optional_packs"),
                uniqueStrings(dependencies, "required_mods"),
                uniqueStrings(dependencies, "optional_mods"),
                uniqueStrings(dependencies, "incompatible_mods"),
                TomlValues.optionalString(pack, "homepage"),
                TomlValues.optionalString(pack, "source"),
                TomlValues.optionalString(pack, "description"),
                TomlValues.optionalString(pack, "changelog_url"),
                uniqueStrings(pack, "feature_flags"),
                optionalId(pack, "exported_asset_pack_id"),
                TomlValues.optionalBoolean(pack, "trusted_scripts", false),
                parsePolicies(policies)
        );
        var provenance = new Provenance(id, "pack.toml", "toml");
        return new PackLayer(source, manifest, provenance);
    }

    private static Optional<ResourceLocation> optionalId(Map<String, Object> values, String key) {
        return TomlValues.optionalString(values, key).map(StableId::parse);
    }

    private static List<PackRequirement> requirements(Map<String, Object> values, String key) {
        return TomlValues.stringList(values, key).stream().map(PackRequirement::parse).toList();
    }

    private static Set<String> uniqueStrings(Map<String, Object> values, String key) {
        List<String> authored = TomlValues.stringList(values, key);
        var unique = new TreeSet<>(authored);
        if (unique.size() != authored.size()) {
            throw new IllegalArgumentException(key + " contains a duplicate entry");
        }
        return Set.copyOf(unique);
    }

    private static PackPolicies parsePolicies(Map<String, Object> values) {
        PackPolicies defaults = PackPolicies.DEFAULT;
        return new PackPolicies(
                parsePolicy(values, "missing_required", defaults.missingRequired(), PackPolicies.MissingRequired.class),
                parsePolicy(values, "missing_optional", defaults.missingOptional(), PackPolicies.MissingOptional.class),
                parsePolicy(values, "unknown_field", defaults.unknownField(), PackPolicies.UnknownField.class),
                parsePolicy(values, "duplicate_id", defaults.duplicateId(), PackPolicies.ConflictPolicy.class),
                parsePolicy(values, "merge_conflict", defaults.mergeConflict(), PackPolicies.ConflictPolicy.class),
                parsePolicy(values, "secret_projection", defaults.secretProjection(), PackPolicies.SecretProjection.class)
        );
    }

    private static <E extends Enum<E>> E parsePolicy(
            Map<String, Object> values,
            String key,
            E fallback,
            Class<E> type
    ) {
        return values.containsKey(key) ? PackPolicies.parse(type, TomlValues.string(values, key)) : fallback;
    }
}
