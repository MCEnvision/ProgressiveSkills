package com.envisione.progressiveskills.common.presentation;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An allowlisted named text color or a canonical lowercase {@code #rrggbb}
 * color. The type carries no client renderer or mutable vanilla text state.
 */
public record TextColorSpec(String value) {
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Set<String> NAMED_COLORS = Set.of(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white"
    );

    public TextColorSpec {
        Objects.requireNonNull(value, "value");
        var canonical = value.toLowerCase(Locale.ROOT);
        if (!NAMED_COLORS.contains(canonical) && !HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("unsupported text color: " + value);
        }
        value = canonical;
    }

    public static TextColorSpec named(String name) {
        return new TextColorSpec(name);
    }

    public static TextColorSpec hex(String value) {
        return new TextColorSpec(value);
    }
}
