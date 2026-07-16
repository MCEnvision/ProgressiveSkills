package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.Alias;
import com.envisione.progressiveskills.common.source.Provenance;

import java.util.Objects;

/** Replacement alias coupled to its authoring source. */
public record ParsedAlias(Alias alias, Provenance provenance) implements Comparable<ParsedAlias> {
    public ParsedAlias {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(provenance, "provenance");
    }

    @Override
    public int compareTo(ParsedAlias other) {
        int aliasComparison = alias.compareTo(other.alias);
        return aliasComparison != 0 ? aliasComparison : provenance.compareTo(other.provenance);
    }
}
