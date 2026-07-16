package com.envisione.progressiveskills.common.presentation;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconSpecTest {
    private static final ComponentSpec ALT = ComponentSpec.localized("icon.test.alt", "Test icon");
    private static final ComponentSpec NARRATION = ComponentSpec.localized("icon.test.narration", "Test icon");

    @Test
    void exposesEveryPlannedIconKindWithUniqueSchemaNames() {
        var names = Arrays.stream(IconKind.values()).map(IconKind::serializedName).toList();

        assertEquals(8, names.size());
        assertEquals(names.size(), new HashSet<>(names).size());
        assertTrue(names.containsAll(List.of(
                "item",
                "block",
                "texture",
                "atlas_sprite",
                "player_head",
                "entity_preview",
                "cycling_tag",
                "composite_badge"
        )));
    }

    @Test
    void singleIconUsesOnlyUnresolvedResourceLocations() {
        var icon = IconSpec.single(
                IconKind.ITEM,
                id("minecraft", "iron_chestplate"),
                id("minecraft", "barrier"),
                ALT,
                NARRATION
        );

        assertEquals(IconKind.ITEM, icon.kind());
        assertEquals(List.of(id("minecraft", "iron_chestplate")), icon.references());
        assertEquals(id("minecraft", "barrier"), icon.fallback());
    }

    @Test
    void copiesOrderedCompositeReferencesAndRequiresAccessibilityText() {
        var references = new ArrayList<>(List.of(
                id("mypack", "icons/base"),
                id("mypack", "icons/badge")
        ));
        var icon = new IconSpec(
                IconKind.COMPOSITE_BADGE,
                references,
                id("progressiveskills", "icons/missing"),
                ALT,
                Optional.of(NARRATION),
                false
        );

        references.clear();

        assertEquals(2, icon.references().size());
        assertThrows(UnsupportedOperationException.class, () -> icon.references().clear());
        assertThrows(
                NullPointerException.class,
                () -> new IconSpec(
                        IconKind.ITEM,
                        List.of(id("minecraft", "stone")),
                        id("minecraft", "barrier"),
                        null,
                        Optional.of(NARRATION),
                        false
                )
        );
    }

    @Test
    void enforcesKindSpecificReferenceBoundsAndUniqueness() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IconSpec(
                        IconKind.ITEM,
                        List.of(id("minecraft", "stone"), id("minecraft", "dirt")),
                        id("minecraft", "barrier"),
                        ALT,
                        Optional.of(NARRATION),
                        false
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IconSpec(
                        IconKind.COMPOSITE_BADGE,
                        List.of(id("mypack", "same"), id("mypack", "same")),
                        id("progressiveskills", "icons/missing"),
                        ALT,
                        Optional.of(NARRATION),
                        false
                )
        );
    }

    @Test
    void entityPreviewRequiresExplicitOptInAndNoOtherKindAcceptsIt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IconSpec(
                        IconKind.ENTITY_PREVIEW,
                        List.of(id("minecraft", "zombie")),
                        id("minecraft", "pig"),
                        ALT,
                        Optional.of(NARRATION),
                        false
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IconSpec(
                        IconKind.ITEM,
                        List.of(id("minecraft", "stone")),
                        id("minecraft", "barrier"),
                        ALT,
                        Optional.of(NARRATION),
                        true
                )
        );

        var preview = IconSpec.entityPreview(
                id("minecraft", "zombie"),
                id("minecraft", "pig"),
                ALT,
                NARRATION
        );
        assertTrue(preview.entityPreviewOptIn());
    }

    @Test
    void narrationDefaultsToAlternativeTextWhenOmitted() {
        var icon = IconSpec.single(
                IconKind.ITEM,
                id("minecraft", "stone"),
                id("minecraft", "barrier"),
                ALT
        );

        assertTrue(icon.narration().isEmpty());
        assertEquals(ALT, icon.effectiveNarration());
    }

    @Test
    void referencesAndFallbacksUseStrictStableIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IconSpec.single(
                        IconKind.ITEM,
                        ResourceLocation.fromNamespaceAndPath("", "stone"),
                        id("minecraft", "barrier"),
                        ALT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> IconSpec.single(
                        IconKind.ITEM,
                        id("minecraft", "stone"),
                        ResourceLocation.fromNamespaceAndPath("minecraft", ""),
                        ALT
                )
        );
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
