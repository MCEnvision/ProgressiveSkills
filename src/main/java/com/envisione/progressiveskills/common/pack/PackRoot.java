package com.envisione.progressiveskills.common.pack;

import java.nio.file.Path;
import java.util.Objects;

/** One trusted discovery root with an explicit precedence tier and stable diagnostics label. */
public record PackRoot(PackRootTier tier, String name, Path path) implements Comparable<PackRoot> {
    public PackRoot {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid pack-root name: " + name);
        }
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public int compareTo(PackRoot other) {
        int tierComparison = tier.compareTo(other.tier);
        return tierComparison != 0 ? tierComparison : name.compareTo(other.name);
    }
}
