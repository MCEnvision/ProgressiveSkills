package com.envisione.progressiveskills.common.pack;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Environment and pack facts pinned beside a last-known-good source generation. */
public record PackLockMetadata(
        int schemaVersion,
        SemanticVersion engineVersion,
        Map<String, String> modVersions,
        Map<ResourceLocation, SemanticVersion> packVersions,
        Map<ResourceLocation, String> packSourceDigests
) {
    public PackLockMetadata {
        if (schemaVersion != 2) {
            throw new IllegalArgumentException("Pack lock schema version must be 2");
        }
        Objects.requireNonNull(engineVersion, "engineVersion");
        modVersions = immutableMods(modVersions);
        packVersions = immutablePackVersions(packVersions);
        packSourceDigests = immutableDigests(packSourceDigests);
        if (!packVersions.keySet().equals(packSourceDigests.keySet())) {
            throw new IllegalArgumentException("Locked pack versions and source digests must have identical ids");
        }
    }

    public static PackLockMetadata capture(AvailableEnvironment environment, PackSnapshot snapshot) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(snapshot, "snapshot");
        var versions = new TreeMap<ResourceLocation, SemanticVersion>(ResourceLocation::compareNamespaced);
        snapshot.manifests().forEach((id, manifest) -> versions.put(id, manifest.contentVersion()));
        var referencedMods = new java.util.TreeSet<String>();
        snapshot.manifests().values().forEach(manifest -> {
            referencedMods.addAll(manifest.requiredMods());
            referencedMods.addAll(manifest.optionalMods());
            referencedMods.addAll(manifest.incompatibleMods());
        });
        var relevantLoadedMods = new TreeMap<String, String>();
        referencedMods.forEach(id -> {
            String version = environment.mods().get(id);
            if (version != null) {
                relevantLoadedMods.put(id, version);
            }
        });
        return new PackLockMetadata(
                2,
                environment.engineVersion(),
                relevantLoadedMods,
                versions,
                snapshot.packSourceDigests()
        );
    }

    public boolean matches(AvailableEnvironment environment, PackSnapshot snapshot) {
        return equals(capture(environment, snapshot));
    }

    private static Map<String, String> immutableMods(Map<String, String> values) {
        Objects.requireNonNull(values, "modVersions");
        if (values.size() > AvailableEnvironment.MAX_MODS) {
            throw new IllegalArgumentException("Locked mod versions exceed " + AvailableEnvironment.MAX_MODS);
        }
        var sorted = new TreeMap<String, String>();
        values.forEach((id, version) -> sorted.put(
                AvailableEnvironment.requireModId(id),
                AvailableEnvironment.requireVersionText(version)
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<ResourceLocation, SemanticVersion> immutablePackVersions(
            Map<ResourceLocation, SemanticVersion> values
    ) {
        Objects.requireNonNull(values, "packVersions");
        if (values.size() > ContentPackDiscoverer.MAX_PACKS) {
            throw new IllegalArgumentException("Locked pack versions exceed " + ContentPackDiscoverer.MAX_PACKS);
        }
        var sorted = new TreeMap<ResourceLocation, SemanticVersion>(ResourceLocation::compareNamespaced);
        values.forEach((id, version) -> sorted.put(
                com.envisione.progressiveskills.common.id.StableId.requireValid(id),
                Objects.requireNonNull(version, "pack version")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<ResourceLocation, String> immutableDigests(Map<ResourceLocation, String> values) {
        Objects.requireNonNull(values, "packSourceDigests");
        var sorted = new TreeMap<ResourceLocation, String>(ResourceLocation::compareNamespaced);
        values.forEach((id, digest) -> {
            var checkedId = com.envisione.progressiveskills.common.id.StableId.requireValid(id);
            Objects.requireNonNull(digest, "pack source digest");
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid locked pack source digest: " + checkedId);
            }
            sorted.put(checkedId, digest);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
