package com.envisione.progressiveskills.server.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Seeds the enabled dependency-free Core pack once without overwriting operator edits. */
public final class StarterPackInstaller {
    private static final String RESOURCE_ROOT = "data/progressiveskills/starter-pack/";
    private static final List<String> RESOURCES = List.of(
            "pack.toml",
            "component_specs/engine_name.toml",
            "currencies/global_points.toml",
            "skills/physique.toml",
            "skills/builder.toml",
            "skills/combat.toml",
            "skills/miner.toml",
            "skills/woodcutting.toml",
            "skills/farming.toml",
            "skills/fishing.toml",
            "skills/hunting.toml",
            "skills/archery.toml",
            "skills/defense.toml",
            "skills/agility.toml",
            "skills/endurance.toml",
            "skills/exploration.toml",
            "skills/alchemy.toml",
            "skills/enchanting.toml",
            "rules/physique_stone_training.toml",
            "rules/physique_first_log.toml",
            "trees/physique_training.toml",
            "trees/builder_mastery.toml",
            "trees/combat_mastery.toml",
            "trees/miner_mastery.toml",
            "trees/woodcutting_mastery.toml",
            "trees/farming_mastery.toml",
            "trees/fishing_mastery.toml",
            "trees/hunting_mastery.toml",
            "trees/archery_mastery.toml",
            "trees/defense_mastery.toml",
            "trees/agility_mastery.toml",
            "trees/endurance_mastery.toml",
            "trees/exploration_mastery.toml",
            "trees/alchemy_mastery.toml",
            "trees/enchanting_mastery.toml",
            "class_slots/combat.toml",
            "classes/warrior.toml",
            "classes/scholar.toml",
            "abilities/warrior_guard.toml",
            "abilities/combat_insight.toml",
            "abilities/second_wind.toml",
            "items/tome_of_physique.toml",
            "items/physique_level_token.toml",
            "items/physique_respec_token.toml"
    );

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
        for (String resource : RESOURCES) {
            copyIfMissing(pack, resource);
        }
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
