package com.envisione.progressiveskills.common.presentation;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Immutable, data-only localized text specification.
 *
 * <p>The fallback is always present, so presentation never depends on a locale
 * entry existing. A localization key is optional. Placeholder declarations
 * are typed schema, not unvalidated runtime objects. Child components support
 * safe structured styling without exposing a mutable vanilla component.</p>
 */
public record ComponentSpec(
        Optional<String> localizationKey,
        String fallback,
        Map<String, PlaceholderType> placeholders,
        StyleSpec style,
        List<ComponentSpec> children
) {
    public static final int MAX_LOCALIZATION_KEY_CODE_POINTS = 256;
    public static final int MAX_FALLBACK_CODE_POINTS = 2_048;
    public static final int MAX_TOTAL_TEXT_CODE_POINTS = 16_384;
    public static final int MAX_PLACEHOLDERS = 64;
    public static final int MAX_PLACEHOLDER_NAME_CODE_POINTS = 64;
    public static final int MAX_CHILDREN_PER_NODE = 64;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODES = 256;

    private static final Pattern LOCALIZATION_KEY = Pattern.compile("[a-z0-9][a-z0-9_.:/-]*");
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-z][a-z0-9_]*");
    private static final String LEGACY_CODES = "0123456789abcdefklmnor";

    public ComponentSpec {
        localizationKey = validateLocalizationKey(localizationKey);
        fallback = validateFallback(fallback);
        placeholders = copyPlaceholders(placeholders);
        style = Objects.requireNonNull(style, "style");
        children = copyChildren(children);

        validatePlaceholderUsage(fallback, placeholders);
        validateTree(fallback, localizationKey, children);
    }

    public static ComponentSpec literal(String fallback) {
        return new ComponentSpec(Optional.empty(), fallback, Map.of(), StyleSpec.EMPTY, List.of());
    }

    public static ComponentSpec localized(String localizationKey, String fallback) {
        return new ComponentSpec(
                Optional.of(Objects.requireNonNull(localizationKey, "localizationKey")),
                fallback,
                Map.of(),
                StyleSpec.EMPTY,
                List.of()
        );
    }

    public static ComponentSpec localized(
            String localizationKey,
            String fallback,
            Map<String, PlaceholderType> placeholders
    ) {
        return new ComponentSpec(
                Optional.of(Objects.requireNonNull(localizationKey, "localizationKey")),
                fallback,
                placeholders,
                StyleSpec.EMPTY,
                List.of()
        );
    }

    private static Optional<String> validateLocalizationKey(Optional<String> localizationKey) {
        Objects.requireNonNull(localizationKey, "localizationKey");
        localizationKey.ifPresent(key -> {
            requireBoundedNonBlank(key, MAX_LOCALIZATION_KEY_CODE_POINTS, "localizationKey");
            if (!LOCALIZATION_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("malformed localization key: " + key);
            }
        });
        return localizationKey;
    }

    private static String validateFallback(String fallback) {
        requireBoundedNonBlank(fallback, MAX_FALLBACK_CODE_POINTS, "fallback");
        validateSafeCharacters(fallback);
        validateAmpersandShorthand(fallback);
        return fallback;
    }

    private static Map<String, PlaceholderType> copyPlaceholders(Map<String, PlaceholderType> placeholders) {
        Objects.requireNonNull(placeholders, "placeholders");
        if (placeholders.size() > MAX_PLACEHOLDERS) {
            throw new IllegalArgumentException("too many placeholders: " + placeholders.size());
        }

        var sorted = new TreeMap<String, PlaceholderType>();
        placeholders.forEach((name, type) -> {
            requireBoundedNonBlank(name, MAX_PLACEHOLDER_NAME_CODE_POINTS, "placeholder name");
            if (!PLACEHOLDER_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("malformed placeholder name: " + name);
            }
            sorted.put(name, Objects.requireNonNull(type, "placeholder type for " + name));
        });
        return Collections.unmodifiableMap(sorted);
    }

    private static List<ComponentSpec> copyChildren(List<ComponentSpec> children) {
        Objects.requireNonNull(children, "children");
        if (children.size() > MAX_CHILDREN_PER_NODE) {
            throw new IllegalArgumentException("too many component children: " + children.size());
        }
        return List.copyOf(children);
    }

    private static void validatePlaceholderUsage(String fallback, Map<String, PlaceholderType> placeholders) {
        var used = parsePlaceholderNames(fallback);
        for (var name : used) {
            if (!placeholders.containsKey(name)) {
                throw new IllegalArgumentException("undeclared placeholder in fallback: " + name);
            }
        }
        for (var name : placeholders.keySet()) {
            if (!used.contains(name)) {
                throw new IllegalArgumentException("placeholder is declared but unused in fallback: " + name);
            }
        }
    }

    private static Set<String> parsePlaceholderNames(String fallback) {
        var names = new HashSet<String>();
        for (int index = 0; index < fallback.length();) {
            char current = fallback.charAt(index);
            if (current == '{') {
                if (index + 1 < fallback.length() && fallback.charAt(index + 1) == '{') {
                    index += 2;
                    continue;
                }
                int close = fallback.indexOf('}', index + 1);
                if (close < 0) {
                    throw new IllegalArgumentException("unclosed placeholder in fallback");
                }
                var name = fallback.substring(index + 1, close);
                requireBoundedNonBlank(name, MAX_PLACEHOLDER_NAME_CODE_POINTS, "placeholder name");
                if (!PLACEHOLDER_NAME.matcher(name).matches()) {
                    throw new IllegalArgumentException("malformed placeholder in fallback: " + name);
                }
                names.add(name);
                index = close + 1;
                continue;
            }
            if (current == '}') {
                if (index + 1 < fallback.length() && fallback.charAt(index + 1) == '}') {
                    index += 2;
                    continue;
                }
                throw new IllegalArgumentException("unmatched closing brace in fallback");
            }
            index++;
        }
        return names;
    }

    private static void validateTree(
            String rootFallback,
            Optional<String> rootLocalizationKey,
            List<ComponentSpec> rootChildren
    ) {
        int nodes = 1;
        int textCodePoints = codePointLength(rootFallback)
                + rootLocalizationKey.map(ComponentSpec::codePointLength).orElse(0);
        var pending = new ArrayDeque<NodeAtDepth>();
        for (var child : rootChildren) {
            pending.addLast(new NodeAtDepth(child, 2));
        }

        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (current.depth() > MAX_DEPTH) {
                throw new IllegalArgumentException("component depth exceeds " + MAX_DEPTH);
            }
            nodes++;
            if (nodes > MAX_NODES) {
                throw new IllegalArgumentException("component node count exceeds " + MAX_NODES);
            }
            textCodePoints += codePointLength(current.component().fallback());
            textCodePoints += current.component().localizationKey()
                    .map(ComponentSpec::codePointLength)
                    .orElse(0);
            if (textCodePoints > MAX_TOTAL_TEXT_CODE_POINTS) {
                throw new IllegalArgumentException(
                        "component text exceeds " + MAX_TOTAL_TEXT_CODE_POINTS + " code points"
                );
            }
            for (var child : current.component().children()) {
                pending.addLast(new NodeAtDepth(child, current.depth() + 1));
            }
        }
    }

    private static void validateSafeCharacters(String value) {
        for (int offset = 0; offset < value.length();) {
            char unit = value.charAt(offset);
            if (Character.isHighSurrogate(unit)) {
                if (offset + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    throw new IllegalArgumentException("fallback contains an unpaired high surrogate");
                }
            } else if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("fallback contains an unpaired low surrogate");
            }
            int codePoint = value.codePointAt(offset);
            if (codePoint == '\u00a7') {
                throw new IllegalArgumentException("raw section-sign formatting is not allowed");
            }
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                throw new IllegalArgumentException("fallback contains a disallowed control character");
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static void validateAmpersandShorthand(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '&') {
                continue;
            }
            if (index + 1 >= value.length()) {
                throw new IllegalArgumentException("trailing ampersand must be escaped as &&");
            }
            char code = Character.toLowerCase(value.charAt(index + 1));
            if (code == '&' || LEGACY_CODES.indexOf(code) >= 0) {
                index++;
                continue;
            }
            if (code == '#') {
                if (index + 7 >= value.length()) {
                    throw new IllegalArgumentException("hex color shorthand must contain six digits");
                }
                for (int digit = index + 2; digit <= index + 7; digit++) {
                    if (Character.digit(value.charAt(digit), 16) < 0) {
                        throw new IllegalArgumentException("malformed hex color shorthand");
                    }
                }
                index += 7;
                continue;
            }
            throw new IllegalArgumentException("unsupported ampersand shorthand: &" + value.charAt(index + 1));
        }
    }

    private static void requireBoundedNonBlank(String value, int maxCodePoints, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (codePointLength(value) > maxCodePoints) {
            throw new IllegalArgumentException(field + " exceeds " + maxCodePoints + " code points");
        }
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private record NodeAtDepth(ComponentSpec component, int depth) {
        private NodeAtDepth {
            Objects.requireNonNull(component, "component");
        }
    }
}
