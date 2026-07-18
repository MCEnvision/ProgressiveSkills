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
import java.util.Properties;

public final class ClientPreferences {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean hudEnabled = true;
    private static boolean highContrast;
    private static boolean reducedMotion;
    private static boolean compactLayout;
    private static int textScale = 100;
    private static HudAnchor hudAnchor = HudAnchor.LOWER_RIGHT;
    private static int hudOffsetX;
    private static int hudOffsetY;
    private static int hudScale = 100;
    private static int hudOpacity = 75;
    private static boolean loaded;

    private ClientPreferences() {
    }

    public static synchronized boolean hudEnabled() {
        load();
        return hudEnabled;
    }

    public static synchronized void toggleHud() {
        load();
        hudEnabled = !hudEnabled;
        save();
    }

    public static synchronized boolean highContrast() {
        load();
        return highContrast;
    }

    public static synchronized void toggleHighContrast() {
        load();
        highContrast = !highContrast;
        save();
    }

    public static synchronized boolean reducedMotion() {
        load();
        return reducedMotion;
    }

    public static synchronized void toggleReducedMotion() {
        load();
        reducedMotion = !reducedMotion;
        save();
    }

    public static synchronized boolean compactLayout() {
        load();
        return compactLayout;
    }

    public static synchronized void toggleCompactLayout() {
        load();
        compactLayout = !compactLayout;
        save();
    }

    public static synchronized int textScale() {
        load();
        return textScale;
    }

    public static synchronized void cycleTextScale() {
        load();
        textScale = switch (textScale) {
            case 100 -> 125;
            case 125 -> 150;
            default -> 100;
        };
        save();
    }

    public static synchronized HudAnchor hudAnchor() {
        load();
        return hudAnchor;
    }

    public static synchronized void cycleHudAnchor() {
        load();
        HudAnchor[] values = HudAnchor.values();
        hudAnchor = values[(hudAnchor.ordinal() + 1) % values.length];
        save();
    }

    public static synchronized int hudOffsetX() {
        load();
        return hudOffsetX;
    }

    public static synchronized int hudOffsetY() {
        load();
        return hudOffsetY;
    }

    public static synchronized void nudgeHud(int x, int y) {
        load();
        hudOffsetX = Math.clamp(hudOffsetX + x, -160, 160);
        hudOffsetY = Math.clamp(hudOffsetY + y, -120, 120);
        save();
    }

    public static synchronized int hudScale() {
        load();
        return hudScale;
    }

    public static synchronized void cycleHudScale() {
        load();
        hudScale = switch (hudScale) {
            case 75 -> 100;
            case 100 -> 125;
            case 125 -> 150;
            default -> 75;
        };
        save();
    }

    public static synchronized int hudOpacity() {
        load();
        return hudOpacity;
    }

    public static synchronized void cycleHudOpacity() {
        load();
        hudOpacity = switch (hudOpacity) {
            case 50 -> 75;
            case 75 -> 100;
            default -> 50;
        };
        save();
    }

    public static synchronized void resetHudLayout() {
        load();
        hudAnchor = HudAnchor.LOWER_RIGHT;
        hudOffsetX = 0;
        hudOffsetY = 0;
        hudScale = 100;
        hudOpacity = 75;
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            return;
        }
        var values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
            hudEnabled = booleanValue(values, "hud", true);
            highContrast = booleanValue(values, "high_contrast", false);
            reducedMotion = booleanValue(values, "reduced_motion", false);
            compactLayout = booleanValue(values, "compact_layout", false);
            int storedScale = Integer.parseInt(values.getProperty("text_scale", "100"));
            textScale = storedScale == 100 || storedScale == 125 || storedScale == 150 ? storedScale : 100;
            hudAnchor = HudAnchor.parse(values.getProperty("hud_anchor", "lower_right"));
            hudOffsetX = boundedInteger(values, "hud_offset_x", -160, 160, 0);
            hudOffsetY = boundedInteger(values, "hud_offset_y", -120, 120, 0);
            int storedHudScale = Integer.parseInt(values.getProperty("hud_scale", "100"));
            hudScale = storedHudScale == 75 || storedHudScale == 100
                    || storedHudScale == 125 || storedHudScale == 150 ? storedHudScale : 100;
            int storedOpacity = Integer.parseInt(values.getProperty("hud_opacity", "75"));
            hudOpacity = storedOpacity == 50 || storedOpacity == 75 || storedOpacity == 100
                    ? storedOpacity : 75;
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("ProgressiveSkills client preferences could not be loaded", exception);
        }
    }

    private static boolean booleanValue(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int boundedInteger(
            Properties values,
            String key,
            int minimum,
            int maximum,
            int fallback
    ) {
        int value = Integer.parseInt(values.getProperty(key, Integer.toString(fallback)));
        return value >= minimum && value <= maximum ? value : fallback;
    }

    private static void save() {
        var values = new Properties();
        values.setProperty("hud", Boolean.toString(hudEnabled));
        values.setProperty("high_contrast", Boolean.toString(highContrast));
        values.setProperty("reduced_motion", Boolean.toString(reducedMotion));
        values.setProperty("compact_layout", Boolean.toString(compactLayout));
        values.setProperty("text_scale", Integer.toString(textScale));
        values.setProperty("hud_anchor", hudAnchor.serializedName());
        values.setProperty("hud_offset_x", Integer.toString(hudOffsetX));
        values.setProperty("hud_offset_y", Integer.toString(hudOffsetY));
        values.setProperty("hud_scale", Integer.toString(hudScale));
        values.setProperty("hud_opacity", Integer.toString(hudOpacity));
        Path target = file();
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
            LOGGER.warn("ProgressiveSkills client preferences could not be saved", exception);
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("progressiveskills-client.properties").toAbsolutePath().normalize();
    }

    public enum HudAnchor {
        UPPER_LEFT("upper_left"),
        UPPER_RIGHT("upper_right"),
        LOWER_LEFT("lower_left"),
        LOWER_RIGHT("lower_right");

        private final String serializedName;

        HudAnchor(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        private static HudAnchor parse(String value) {
            for (HudAnchor anchor : values()) {
                if (anchor.serializedName.equals(value)) {
                    return anchor;
                }
            }
            return LOWER_RIGHT;
        }
    }
}
