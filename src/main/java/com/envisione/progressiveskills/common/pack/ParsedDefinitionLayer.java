package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.source.SourceReference;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Syntax-normalized definition layer before merge/patch and typed schema compilation. */
public record ParsedDefinitionLayer(
        PackLayer pack,
        DefinitionKey key,
        PackMergeIntent mergeIntent,
        Optional<String> expectedOldDigest,
        Map<String, Object> fields,
        List<DefinitionPatch> patches,
        List<SourceReference> patchSources,
        Provenance provenance,
        SourceMap sourceMap
) implements Comparable<ParsedDefinitionLayer> {
    public ParsedDefinitionLayer {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mergeIntent, "mergeIntent");
        Objects.requireNonNull(expectedOldDigest, "expectedOldDigest");
        fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
        patches = List.copyOf(patches);
        patchSources = List.copyOf(patchSources);
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(sourceMap, "sourceMap");
        if (mergeIntent == PackMergeIntent.PATCH != !patches.isEmpty()) {
            throw new IllegalArgumentException("Only patch layers declare [[patches]]");
        }
        if (patchSources.size() != patches.size()) {
            throw new IllegalArgumentException("Every patch requires one source reference");
        }
        if (mergeIntent != PackMergeIntent.REPLACE && expectedOldDigest.isPresent()) {
            throw new IllegalArgumentException("expected_old_digest is valid only for replace");
        }
        expectedOldDigest.ifPresent(digest -> {
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("expected_old_digest must be lowercase SHA-256");
            }
        });
    }

    @Override
    public int compareTo(ParsedDefinitionLayer other) {
        int packComparison = pack.compareTo(other.pack);
        if (packComparison != 0) {
            return packComparison;
        }
        int keyComparison = key.compareTo(other.key);
        return keyComparison != 0 ? keyComparison : provenance.compareTo(other.provenance);
    }
}
