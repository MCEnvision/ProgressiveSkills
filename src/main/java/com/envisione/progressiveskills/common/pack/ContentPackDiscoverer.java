package com.envisione.progressiveskills.common.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Bounded, non-recursive discovery of pack directories beneath trusted roots. */
public final class ContentPackDiscoverer {
    public static final int MAX_ROOTS = 64;
    public static final int MAX_PACKS = 1_024;

    public List<PackSource> discover(Collection<PackRoot> roots) throws IOException {
        Objects.requireNonNull(roots, "roots");
        if (roots.size() > MAX_ROOTS) {
            throw new IllegalArgumentException("Pack roots exceed " + MAX_ROOTS);
        }
        var orderedRoots = roots.stream().map(root -> Objects.requireNonNull(root, "root")).sorted().toList();
        var sources = new ArrayList<PackSource>();
        for (PackRoot root : orderedRoots) {
            if (!Files.exists(root.path(), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(root.path()) || !Files.isDirectory(root.path(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Pack root must be a non-symbolic directory: " + root.path());
            }
            try (var entries = Files.list(root.path())) {
                for (var path : entries.sorted(Comparator.comparing(candidate -> candidate.getFileName().toString())).toList()) {
                    if (Files.isSymbolicLink(path)) {
                        throw new IllegalArgumentException("Symbolic links are not allowed in pack roots: " + path);
                    }
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IllegalArgumentException("Only pack directories are allowed directly beneath a pack root: " + path);
                    }
                    var source = new PackSource(root, path.getFileName().toString(), path);
                    if (!Files.isRegularFile(source.manifestPath(), LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(source.manifestPath())) {
                        throw new IllegalArgumentException("Pack directory is missing a regular pack.toml: " + path);
                    }
                    sources.add(source);
                    if (sources.size() > MAX_PACKS) {
                        throw new IllegalArgumentException("Discovered packs exceed " + MAX_PACKS);
                    }
                }
            }
        }
        return List.copyOf(sources);
    }
}
