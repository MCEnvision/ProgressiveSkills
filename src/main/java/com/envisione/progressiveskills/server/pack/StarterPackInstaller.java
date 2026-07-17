package com.envisione.progressiveskills.server.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Seeds the enabled dependency-free Core pack once without overwriting operator edits. */
public final class StarterPackInstaller {
    private static final String RESOURCE_ROOT = "data/progressiveskills/starter-pack/";

    private StarterPackInstaller() {}

    public static Path install(Path packsRoot) throws IOException {
        Path root = packsRoot.toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(root, "Pack root");
        } else {
            Files.createDirectories(root);
        }
        Path pack = root.resolve("progressiveskills-core");
        if (Files.exists(pack, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(pack, "Starter-pack directory");
        } else {
            Files.createDirectory(pack);
        }
        copyIfMissing(pack, "pack.toml");
        copyIfMissing(pack, "component_specs/engine_name.toml");
        copyIfMissing(pack, "currencies/global_points.toml");
        copyIfMissing(pack, "skills/physique.toml");
        copyIfMissing(pack, "rules/physique_stone_training.toml");
        copyIfMissing(pack, "rules/physique_first_log.toml");
        return pack;
    }

    private static void copyIfMissing(Path pack, String relative) throws IOException {
        Path output = pack.resolve(relative).normalize();
        if (!output.startsWith(pack)) {
            throw new IOException("Starter-pack resource escapes its destination");
        }
        Path parent = output.getParent();
        Path current = pack;
        for (Path segment : pack.relativize(parent)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(current, "Starter-pack parent");
            } else {
                Files.createDirectory(current);
            }
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(output) || !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Starter-pack destination is not a regular file: " + output);
            }
            return;
        }
        try (InputStream input = StarterPackInstaller.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + relative)) {
            if (input == null) {
                throw new IOException("Missing bundled starter-pack resource: " + relative);
            }
            Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isDirectory(temporary, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Starter-pack temporary path is a directory: " + temporary);
                }
                Files.delete(temporary);
            }
            Files.copy(input, temporary);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, output);
            }
        }
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a non-symbolic directory: " + path);
        }
    }
}
