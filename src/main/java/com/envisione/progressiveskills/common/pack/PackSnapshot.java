package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable, validated whole-pack staging snapshot suitable for atomic publication. */
public record PackSnapshot(
        Map<ResourceLocation, PackManifest> manifests,
        Map<ResourceLocation, String> packSourceDigests,
        CanonicalIr canonicalIr,
        Set<DefinitionKey> disabledDefinitions,
        String contentDigest,
        SourceBundle sourceBundle
) {
    public PackSnapshot {
        Objects.requireNonNull(manifests, "manifests");
        var sortedManifests = new TreeMap<ResourceLocation, PackManifest>(ResourceLocation::compareNamespaced);
        manifests.forEach((id, manifest) -> {
            Objects.requireNonNull(id, "manifest id");
            Objects.requireNonNull(manifest, "manifest");
            if (!id.equals(manifest.id())) {
                throw new IllegalArgumentException("Manifest map key does not match manifest id: " + id);
            }
            sortedManifests.put(id, manifest);
        });
        manifests = Collections.unmodifiableMap(new LinkedHashMap<>(sortedManifests));
        Objects.requireNonNull(packSourceDigests, "packSourceDigests");
        var sortedSourceDigests = new TreeMap<ResourceLocation, String>(ResourceLocation::compareNamespaced);
        packSourceDigests.forEach((id, digest) -> {
            Objects.requireNonNull(id, "pack source digest id");
            Objects.requireNonNull(digest, "pack source digest");
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Pack source digest must be lowercase SHA-256: " + id);
            }
            sortedSourceDigests.put(id, digest);
        });
        if (!sortedSourceDigests.keySet().equals(sortedManifests.keySet())) {
            throw new IllegalArgumentException("Pack source digest ids must exactly match manifest ids");
        }
        packSourceDigests = Collections.unmodifiableMap(new LinkedHashMap<>(sortedSourceDigests));
        Objects.requireNonNull(canonicalIr, "canonicalIr");
        disabledDefinitions = Collections.unmodifiableSet(new TreeSet<>(disabledDefinitions));
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (!contentDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Content digest must be lowercase SHA-256");
        }
        Objects.requireNonNull(sourceBundle, "sourceBundle");
    }

    public static PackSnapshot create(
            List<PackLayer> packs,
            CanonicalIr canonicalIr,
            Set<DefinitionKey> disabledDefinitions,
            SourceBundle sourceBundle
    ) {
        var manifests = new TreeMap<ResourceLocation, PackManifest>(ResourceLocation::compareNamespaced);
        packs.forEach(pack -> manifests.put(pack.manifest().id(), pack.manifest()));
        var sourceDigests = new TreeMap<ResourceLocation, String>(ResourceLocation::compareNamespaced);
        packs.forEach(pack -> sourceDigests.put(
                pack.manifest().id(),
                sourceBundle.digestPrefix(pack.source().bundlePrefix() + "/")
        ));
        String digest = computeDigest(manifests, canonicalIr, disabledDefinitions);
        return new PackSnapshot(manifests, sourceDigests, canonicalIr, disabledDefinitions, digest, sourceBundle);
    }

    public static PackSnapshot empty() {
        return create(List.of(), CanonicalIr.of(List.of(), com.envisione.progressiveskills.common.id.AliasMap.empty()),
                Set.of(), SourceBundle.empty());
    }

    private static String computeDigest(
            Map<ResourceLocation, PackManifest> manifests,
            CanonicalIr canonicalIr,
            Set<DefinitionKey> disabledDefinitions
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "progressiveskills-pack-snapshot-v1");
            update(digest, CanonicalSemanticDigest.ir(canonicalIr.semanticProjection(), disabledDefinitions));
            for (var entry : manifests.entrySet()) {
                update(digest, entry.getKey().toString());
                update(digest, CanonicalSemanticDigest.manifest(entry.getValue()));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
