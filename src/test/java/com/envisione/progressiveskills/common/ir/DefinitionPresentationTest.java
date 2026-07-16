package com.envisione.progressiveskills.common.ir;

import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionPresentationTest {
    @Test
    void searchAliasesRejectControlsAndUnpairedSurrogates() {
        assertThrows(IllegalArgumentException.class, () -> presentation(Set.of("line\nbreak")));
        assertThrows(IllegalArgumentException.class, () -> presentation(Set.of("hidden\u0000value")));
        assertThrows(IllegalArgumentException.class, () -> presentation(Set.of("high\ud800")));
        assertThrows(IllegalArgumentException.class, () -> presentation(Set.of("low\udc00")));
    }

    @Test
    void searchAliasesAcceptPairedSupplementaryCharacters() {
        DefinitionPresentation presentation = presentation(Set.of("  Power \ud83d\udca5  "));

        assertEquals(Set.of("Power \ud83d\udca5"), presentation.searchAliases());
    }

    @Test
    void searchAliasCountAndLengthAreBounded() {
        var tooMany = IntStream.rangeClosed(0, DefinitionPresentation.MAX_SEARCH_ALIASES)
                .mapToObj(index -> "alias" + index)
                .collect(Collectors.toSet());

        assertThrows(IllegalArgumentException.class, () -> presentation(tooMany));
        assertThrows(
                IllegalArgumentException.class,
                () -> presentation(Set.of("x".repeat(DefinitionPresentation.MAX_SEARCH_ALIAS_CODE_POINTS + 1)))
        );
    }

    private static DefinitionPresentation presentation(Set<String> aliases) {
        return new DefinitionPresentation(
                ComponentSpec.literal("Physique"),
                Optional.empty(),
                IconSpec.single(
                        IconKind.ITEM,
                        ResourceLocation.parse("minecraft:iron_chestplate"),
                        ResourceLocation.parse("minecraft:barrier"),
                        ComponentSpec.literal("Iron chestplate"),
                        ComponentSpec.literal("Physique skill icon")
                ),
                aliases
        );
    }
}
