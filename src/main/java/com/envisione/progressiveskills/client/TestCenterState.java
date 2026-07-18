package com.envisione.progressiveskills.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class TestCenterState {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final List<String> CHECKS = List.of(
            "Join a world and confirm progression synchronization.",
            "Earn XP from natural and creative placed blocks.",
            "Confirm survival placed blocks do not award protected XP.",
            "Buy and refund a dependent tree node.",
            "Select, swap, and respec a class.",
            "Assign, toggle, and activate an ability.",
            "Use every carrier kind and recover a pending claim.",
            "Open every progression tab using only the keyboard.",
            "Confirm HUD, high contrast, and text size preferences.",
            "Run doctor, why, and the command palette.",
            "Create and share a build code.",
            "Complete a milestone choice and training contract.",
            "Create a party and verify readiness privacy.",
            "Award shared progress and inspect its contribution receipt.",
            "Preview a season leaderboard and rollover.",
            "Create, lint, diff, publish, and roll back a Studio draft.",
            "Import a safe pack and reject an unsafe pack.",
            "Relog, die, change dimension, and restart the server.",
            "Join with two clients and reject stale or replayed actions.",
            "Play normally for fifteen minutes and record any issue."
    );
    private static final Map<Integer, Result> RESULTS = new LinkedHashMap<>();
    private static boolean loaded;

    private TestCenterState() {
    }

    public static synchronized Result result(int index) {
        load();
        return RESULTS.getOrDefault(index, Result.PENDING);
    }

    public static synchronized void cycle(int index) {
        load();
        RESULTS.put(index, switch (result(index)) {
            case PENDING -> Result.PASS;
            case PASS -> Result.FAIL;
            case FAIL -> Result.PENDING;
        });
        save();
    }

    public static synchronized void reset() {
        load();
        RESULTS.clear();
        save();
    }

    public static synchronized Map<Integer, Result> results() {
        load();
        return Collections.unmodifiableMap(new LinkedHashMap<>(RESULTS));
    }

    public static synchronized Path export(String checkpointIdentity) {
        load();
        String identity = boundedIdentity(checkpointIdentity);
        Path directory = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("progressiveskills/test-center").toAbsolutePath().normalize();
        Path target = directory.resolve("mass-check-" + Instant.now().toEpochMilli() + ".txt");
        var text = new StringBuilder();
        text.append("ProgressiveSkills mass check\n");
        text.append("Checkpoint ").append(identity).append('\n');
        text.append("Exported ").append(Instant.now()).append("\n\n");
        for (int index = 0; index < CHECKS.size(); index++) {
            text.append(index + 1).append(". ").append(result(index)).append(". ")
                    .append(CHECKS.get(index)).append('\n');
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(target, text, StandardOpenOption.CREATE_NEW);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("Test Center results could not be exported", exception);
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = stateFile();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            return;
        }
        var values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
            for (int index = 0; index < CHECKS.size(); index++) {
                String stored = values.getProperty(Integer.toString(index));
                if (stored != null) {
                    RESULTS.put(index, Result.valueOf(stored));
                }
            }
        } catch (RuntimeException | IOException exception) {
            RESULTS.clear();
            LOGGER.warn("ProgressiveSkills Test Center state could not be loaded", exception);
        }
    }

    private static void save() {
        var values = new Properties();
        RESULTS.forEach((index, result) -> values.setProperty(Integer.toString(index), result.name()));
        Path target = stateFile();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                values.store(output, null);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("ProgressiveSkills Test Center state could not be saved", exception);
        }
    }

    private static Path stateFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("progressiveskills-test-center.properties").toAbsolutePath().normalize();
    }

    private static String boundedIdentity(String value) {
        String result = value == null ? "unavailable" : value.replace('\n', ' ').replace('\r', ' ').strip();
        if (result.isEmpty()) {
            return "unavailable";
        }
        return result.substring(0, Math.min(result.length(), 512));
    }

    public enum Result {
        PENDING,
        PASS,
        FAIL
    }
}
