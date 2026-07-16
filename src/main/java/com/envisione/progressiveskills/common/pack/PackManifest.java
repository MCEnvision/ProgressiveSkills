package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Immutable canonical manifest metadata for one discovered content pack. */
public record PackManifest(
        int schemaVersion,
        ResourceLocation id,
        String namespace,
        ComponentSpec name,
        SemanticVersion contentVersion,
        VersionConstraint engine,
        List<String> authors,
        String license,
        int priority,
        String defaultLocale,
        Optional<ResourceLocation> defaultTheme,
        Optional<ResourceLocation> defaultLayout,
        List<PackRequirement> requiredPacks,
        List<PackRequirement> optionalPacks,
        Set<String> requiredMods,
        Set<String> optionalMods,
        Set<String> incompatibleMods,
        Optional<String> homepage,
        Optional<String> sourceUrl,
        Optional<String> description,
        Optional<String> changelogUrl,
        Set<String> featureFlags,
        Optional<ResourceLocation> exportedAssetPackId,
        boolean trustedScripts,
        PackPolicies policies
) {
    public static final int MAX_AUTHORS = 64;
    public static final int MAX_DEPENDENCIES_PER_KIND = 256;
    public static final int MIN_PRIORITY = -1_000_000;
    public static final int MAX_PRIORITY = 1_000_000;
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}_[a-z]{2}");

    public PackManifest {
        if (schemaVersion != 2) {
            throw new IllegalArgumentException("Pack manifest schema_version must be 2");
        }
        id = StableId.requireValid(id);
        namespace = StableId.requireNamespace(namespace);
        if (!id.getNamespace().equals(namespace)) {
            throw new IllegalArgumentException("Pack id namespace must equal [pack].namespace: " + id);
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(engine, "engine");
        if (!engine.hasLowerBound() || !engine.hasUpperBound()) {
            throw new IllegalArgumentException("Engine compatibility must have lower and upper bounds");
        }
        authors = checkedTextList(authors, MAX_AUTHORS, "authors");
        license = requireText(license, 256, "license");
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("Pack priority must be within " + MIN_PRIORITY + ".." + MAX_PRIORITY);
        }
        defaultLocale = requireText(defaultLocale, 16, "defaultLocale");
        if (!LOCALE.matcher(defaultLocale).matches()) {
            throw new IllegalArgumentException("Default locale must use lowercase language_country: " + defaultLocale);
        }
        defaultTheme = checkedOptionalId(defaultTheme, "defaultTheme");
        defaultLayout = checkedOptionalId(defaultLayout, "defaultLayout");
        requiredPacks = checkedRequirements(requiredPacks, "requiredPacks");
        optionalPacks = checkedRequirements(optionalPacks, "optionalPacks");
        requiredMods = checkedIds(requiredMods, "requiredMods");
        optionalMods = checkedIds(optionalMods, "optionalMods");
        incompatibleMods = checkedIds(incompatibleMods, "incompatibleMods");
        homepage = checkedOptionalText(homepage, "homepage");
        sourceUrl = checkedOptionalText(sourceUrl, "sourceUrl");
        description = checkedOptionalText(description, "description");
        changelogUrl = checkedOptionalText(changelogUrl, "changelogUrl");
        featureFlags = checkedTextSet(featureFlags, "featureFlags");
        exportedAssetPackId = checkedOptionalId(exportedAssetPackId, "exportedAssetPackId");
        Objects.requireNonNull(policies, "policies");

        var requiredPackIds = requiredPacks.stream().map(PackRequirement::packId).collect(java.util.stream.Collectors.toSet());
        if (optionalPacks.stream().map(PackRequirement::packId).anyMatch(requiredPackIds::contains)) {
            throw new IllegalArgumentException("A pack cannot be both required and optional");
        }
        if (requiredMods.stream().anyMatch(optionalMods::contains)
                || requiredMods.stream().anyMatch(incompatibleMods::contains)
                || optionalMods.stream().anyMatch(incompatibleMods::contains)) {
            throw new IllegalArgumentException("A mod cannot appear in conflicting dependency sets");
        }
    }

    private static Optional<ResourceLocation> checkedOptionalId(Optional<ResourceLocation> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(StableId::requireValid);
    }

    private static Optional<String> checkedOptionalText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(text -> requireText(text, 2_048, name));
    }

    private static Set<String> checkedTextSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > MAX_DEPENDENCIES_PER_KIND) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_DEPENDENCIES_PER_KIND + " entries");
        }
        var sorted = new TreeSet<String>();
        values.forEach(value -> sorted.add(requireText(value, 128, name + " entry")));
        return Collections.unmodifiableSet(sorted);
    }

    private static List<PackRequirement> checkedRequirements(List<PackRequirement> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > MAX_DEPENDENCIES_PER_KIND) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_DEPENDENCIES_PER_KIND + " entries");
        }
        var sorted = new java.util.TreeMap<String, PackRequirement>();
        for (var value : values) {
            var checked = Objects.requireNonNull(value, name + " entry");
            if (sorted.putIfAbsent(checked.packId().toString(), checked) != null) {
                throw new IllegalArgumentException("Duplicate " + name + " entry: " + checked.packId());
            }
        }
        return List.copyOf(sorted.values());
    }

    private static Set<String> checkedIds(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > MAX_DEPENDENCIES_PER_KIND) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_DEPENDENCIES_PER_KIND + " entries");
        }
        var sorted = new TreeSet<String>();
        values.forEach(value -> {
            Objects.requireNonNull(value, name + " entry");
            if (!value.matches("[a-z][a-z0-9_]{1,63}")) {
                throw new IllegalArgumentException("Invalid mod id in " + name + ": " + value);
            }
            sorted.add(value);
        });
        return Collections.unmodifiableSet(sorted);
    }

    private static List<String> checkedTextList(List<String> values, int maximum, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum) {
            throw new IllegalArgumentException(name + " exceeds " + maximum + " entries");
        }
        return values.stream().map(value -> requireText(value, 256, name + " entry")).toList();
    }

    private static String requireText(String value, int maximumCodePoints, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(name + " must contain 1.." + maximumCodePoints + " code points");
        }
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                throw new IllegalArgumentException(name + " contains a disallowed control character");
            }
        });
        return normalized;
    }
}
