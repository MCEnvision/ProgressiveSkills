package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Safe presentation metadata shared by future concrete definition types. */
public record DefinitionPresentation(
        ComponentSpec display,
        Optional<ComponentSpec> description,
        IconSpec icon,
        Set<String> searchAliases
) {
    public static final int MAX_SEARCH_ALIASES = 64;
    public static final int MAX_SEARCH_ALIAS_CODE_POINTS = 128;

    public DefinitionPresentation {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(searchAliases, "searchAliases");
        if (searchAliases.size() > MAX_SEARCH_ALIASES) {
            throw new IllegalArgumentException("Too many search aliases: " + searchAliases.size());
        }
        var sorted = new TreeSet<String>();
        for (var alias : searchAliases) {
            Objects.requireNonNull(alias, "search alias");
            validateSafeSearchAlias(alias);
            var normalized = alias.strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Search aliases must not be blank");
            }
            if (normalized.codePointCount(0, normalized.length()) > MAX_SEARCH_ALIAS_CODE_POINTS) {
                throw new IllegalArgumentException(
                        "Search alias exceeds " + MAX_SEARCH_ALIAS_CODE_POINTS + " code points"
                );
            }
            sorted.add(normalized);
        }
        searchAliases = Collections.unmodifiableSet(sorted);
    }

    private static void validateSafeSearchAlias(String alias) {
        for (int index = 0; index < alias.length();) {
            char unit = alias.charAt(index);
            if (Character.isHighSurrogate(unit)
                    && (index + 1 >= alias.length() || !Character.isLowSurrogate(alias.charAt(index + 1)))) {
                throw new IllegalArgumentException("Search alias contains an unpaired high surrogate");
            }
            if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("Search alias contains an unpaired low surrogate");
            }
            int codePoint = alias.codePointAt(index);
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("Search alias contains a control character");
            }
            index += Character.charCount(codePoint);
        }
    }
}
