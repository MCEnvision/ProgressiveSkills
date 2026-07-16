package com.envisione.progressiveskills.common.presentation;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Safe formatting metadata for a {@link ComponentSpec}.
 *
 * <p>The deliberately small allowlist contains only color, text decorations,
 * and a resource-located font. Click events, selectors, NBT, commands, URLs,
 * insertion text, and renderer objects cannot be represented.</p>
 */
public record StyleSpec(
        Optional<TextColorSpec> color,
        Set<TextDecoration> decorations,
        Optional<ResourceLocation> font
) {
    public static final StyleSpec EMPTY = new StyleSpec(Optional.empty(), Set.of(), Optional.empty());

    public StyleSpec {
        color = Objects.requireNonNull(color, "color");
        font = Objects.requireNonNull(font, "font").map(StableId::requireValid);
        Objects.requireNonNull(decorations, "decorations");

        var copiedDecorations = decorations.isEmpty()
                ? EnumSet.noneOf(TextDecoration.class)
                : EnumSet.copyOf(decorations);
        decorations = Collections.unmodifiableSet(copiedDecorations);
    }

    public static StyleSpec of(TextColorSpec color, Set<TextDecoration> decorations) {
        return new StyleSpec(Optional.of(Objects.requireNonNull(color, "color")), decorations, Optional.empty());
    }
}
