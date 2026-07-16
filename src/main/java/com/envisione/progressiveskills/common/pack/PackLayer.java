package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.source.Provenance;

import java.util.Objects;

/** Parsed pack manifest coupled to its deterministic discovery source. */
public record PackLayer(PackSource source, PackManifest manifest, Provenance provenance)
        implements Comparable<PackLayer> {
    public PackLayer {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(provenance, "provenance");
    }

    @Override
    public int compareTo(PackLayer other) {
        int tierComparison = source.root().tier().compareTo(other.source.root().tier());
        if (tierComparison != 0) {
            return tierComparison;
        }
        int priorityComparison = Integer.compare(manifest.priority(), other.manifest.priority());
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        int idComparison = manifest.id().compareNamespaced(other.manifest.id());
        return idComparison != 0 ? idComparison : source.compareTo(other.source);
    }
}
