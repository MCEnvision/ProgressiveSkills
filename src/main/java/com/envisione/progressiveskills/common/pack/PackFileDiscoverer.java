package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Recursively discovers bounded TOML definitions only beneath registered kind directories. */
public final class PackFileDiscoverer {
    public static final int MAX_FILES_PER_PACK = 8_192;
    private static final int MAX_ENTRIES_PER_KIND = 16_384;
    private static final int MAX_SOURCE_DEPTH = 64;

    public List<DefinitionFile> discover(PackLayer pack) throws IOException {
        var files = new ArrayList<DefinitionFile>();
        for (DefinitionKind kind : DefinitionKinds.all()) {
            Path directory = pack.source().directory().resolve(kind.sourceDirectory());
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Definition kind path must be a regular directory: " + directory);
            }
            try (var paths = Files.walk(directory)) {
                int entries = 0;
                for (Path path : paths.sorted(Comparator.comparing(Path::toString)).toList()) {
                    if (path.equals(directory)) {
                        continue;
                    }
                    entries++;
                    if (entries > MAX_ENTRIES_PER_KIND) {
                        throw new IllegalArgumentException("Definition-kind entries exceed " + MAX_ENTRIES_PER_KIND);
                    }
                    if (directory.relativize(path).getNameCount() > MAX_SOURCE_DEPTH) {
                        throw new IllegalArgumentException("Definition source depth exceeds " + MAX_SOURCE_DEPTH + ": " + path);
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw new IllegalArgumentException("Symbolic links are not allowed in content packs: " + path);
                    }
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            || !path.getFileName().toString().endsWith(".toml")) {
                        throw new IllegalArgumentException("Definition sources must be lowercase .toml files: " + path);
                    }
                    files.add(new DefinitionFile(kind, path.toAbsolutePath().normalize()));
                    if (files.size() > MAX_FILES_PER_PACK) {
                        throw new IllegalArgumentException("Pack definition files exceed " + MAX_FILES_PER_PACK);
                    }
                }
            }
        }
        return List.copyOf(files);
    }

    public record DefinitionFile(DefinitionKind kind, Path path) {
        public DefinitionFile {
            java.util.Objects.requireNonNull(kind, "kind");
            java.util.Objects.requireNonNull(path, "path");
        }
    }
}
