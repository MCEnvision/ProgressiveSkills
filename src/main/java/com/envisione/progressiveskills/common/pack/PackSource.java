package com.envisione.progressiveskills.common.pack;

import java.nio.file.Path;
import java.util.Objects;

/** One bounded discovered pack directory before manifest parsing. */
public record PackSource(PackRoot root, String directoryName, Path directory) implements Comparable<PackSource> {
    public PackSource {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(directoryName, "directoryName");
        if (!directoryName.matches("[a-z0-9][a-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Invalid pack directory name: " + directoryName);
        }
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (!directory.startsWith(root.path())) {
            throw new IllegalArgumentException("Pack source escapes its root: " + directory);
        }
    }

    public Path manifestPath() {
        return directory.resolve("pack.toml");
    }

    public String bundlePrefix() {
        return root.tier().name().toLowerCase(java.util.Locale.ROOT) + "/"
                + root.name() + "/" + directoryName;
    }

    @Override
    public int compareTo(PackSource other) {
        int rootComparison = root.compareTo(other.root);
        return rootComparison != 0 ? rootComparison : directoryName.compareTo(other.directoryName);
    }
}
