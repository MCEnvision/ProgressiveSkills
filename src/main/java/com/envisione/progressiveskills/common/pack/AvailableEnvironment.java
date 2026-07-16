package com.envisione.progressiveskills.common.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Version facts injected by the runtime without exposing loader types to the pack compiler. */
public record AvailableEnvironment(SemanticVersion engineVersion, Map<String, String> mods) {
    public static final int MAX_MODS = 4_096;

    public AvailableEnvironment {
        Objects.requireNonNull(engineVersion, "engineVersion");
        Objects.requireNonNull(mods, "mods");
        if (mods.size() > MAX_MODS) {
            throw new IllegalArgumentException("Available mods exceed " + MAX_MODS);
        }
        var sorted = new TreeMap<String, String>();
        mods.forEach((id, version) -> sorted.put(
                requireModId(id),
                requireVersionText(version)
        ));
        mods = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static AvailableEnvironment empty() {
        return new AvailableEnvironment(SemanticVersion.ENGINE_CURRENT, Map.of());
    }

    static String requireVersionText(String value) {
        Objects.requireNonNull(value, "mod version");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 256) {
            throw new IllegalArgumentException("Invalid mod version text");
        }
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("Mod version text contains a control character");
            }
        });
        return normalized;
    }

    static String requireModId(String value) {
        Objects.requireNonNull(value, "mod id");
        if (!value.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new IllegalArgumentException("Invalid mod id: " + value);
        }
        return value;
    }
}
