package com.envisione.progressiveskills.common.presentation;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentSpecTest {
    @Test
    void localizedComponentPreservesSafeShorthandAndSortsTypedPlaceholders() {
        var declarations = new HashMap<String, PlaceholderType>();
        declarations.put("skill", PlaceholderType.TEXT);
        declarations.put("level", PlaceholderType.INTEGER);

        var spec = new ComponentSpec(
                Optional.of("message.mypack.level_up"),
                "&a{skill} reached level &#12ABef{level}&r",
                declarations,
                new StyleSpec(
                        Optional.of(TextColorSpec.named("GOLD")),
                        EnumSet.of(TextDecoration.BOLD, TextDecoration.ITALIC),
                        Optional.of(id("minecraft", "default"))
                ),
                List.of(ComponentSpec.literal("&7!"))
        );

        declarations.clear();

        assertEquals(List.of("level", "skill"), new ArrayList<>(spec.placeholders().keySet()));
        assertEquals("&a{skill} reached level &#12ABef{level}&r", spec.fallback());
        assertEquals("gold", spec.style().color().orElseThrow().value());
        assertEquals(1, spec.children().size());
        assertThrows(UnsupportedOperationException.class, () -> spec.placeholders().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.children().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.style().decorations().clear());
    }

    @Test
    void literalComponentsNeedNoLocalizationKey() {
        var spec = ComponentSpec.literal("Plain fallback");

        assertTrue(spec.localizationKey().isEmpty());
        assertEquals(StyleSpec.EMPTY, spec.style());
        assertTrue(spec.placeholders().isEmpty());
    }

    @Test
    void rejectsUnsafeOrMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal(" "));
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("raw §cformat"));
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("bad &zcode"));
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("bad &#12345"));
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("bad\u0000text"));
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("bad \ud800 text"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ComponentSpec(
                        Optional.of("Bad Key"),
                        "fallback",
                        Map.of(),
                        StyleSpec.EMPTY,
                        List.of()
                )
        );
    }

    @Test
    void rejectsMalformedOrMismatchedPlaceholderSchemas() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ComponentSpec.localized("message.test", "Hello {name}", Map.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ComponentSpec.localized(
                        "message.test",
                        "Hello",
                        Map.of("name", PlaceholderType.TEXT)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ComponentSpec.localized(
                        "message.test",
                        "Hello {DisplayName}",
                        Map.of("DisplayName", PlaceholderType.TEXT)
                )
        );
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("Hello {name"));
        assertThrows(IllegalArgumentException.class, () -> ComponentSpec.literal("Hello name}"));

        var escaped = ComponentSpec.literal("Literal {{name}} and && symbol");
        assertTrue(escaped.placeholders().isEmpty());
    }

    @Test
    void rejectsOversizedStringsAndDeepTrees() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ComponentSpec.literal("x".repeat(ComponentSpec.MAX_FALLBACK_CODE_POINTS + 1))
        );

        var current = ComponentSpec.literal("leaf");
        for (int depth = 1; depth < ComponentSpec.MAX_DEPTH; depth++) {
            current = parentOf(current);
        }
        var maximumDepth = current;

        assertFalse(maximumDepth.children().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> parentOf(maximumDepth));
    }

    @Test
    void rejectsOversizedChildNodeAndAggregateTextCollections() {
        var tooManyDirectChildren = new ArrayList<ComponentSpec>();
        for (int index = 0; index <= ComponentSpec.MAX_CHILDREN_PER_NODE; index++) {
            tooManyDirectChildren.add(ComponentSpec.literal("child " + index));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> componentWithChildren("root", tooManyDirectChildren)
        );

        var branchChildren = new ArrayList<ComponentSpec>();
        for (int index = 0; index < ComponentSpec.MAX_CHILDREN_PER_NODE; index++) {
            branchChildren.add(ComponentSpec.literal("leaf " + index));
        }
        var branch = componentWithChildren("branch", branchChildren);
        assertThrows(
                IllegalArgumentException.class,
                () -> componentWithChildren("root", List.of(branch, branch, branch, branch))
        );

        var longChild = ComponentSpec.literal("x".repeat(ComponentSpec.MAX_FALLBACK_CODE_POINTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> componentWithChildren("root", List.of(
                        longChild,
                        longChild,
                        longChild,
                        longChild,
                        longChild,
                        longChild,
                        longChild,
                        longChild
                ))
        );
    }

    @Test
    void styleAllowsOnlyCanonicalColorsAndSafeFormattingData() {
        assertEquals("#abcdef", TextColorSpec.hex("#ABCDEF").value());
        assertThrows(IllegalArgumentException.class, () -> TextColorSpec.named("chartreuse"));
        assertThrows(IllegalArgumentException.class, () -> TextColorSpec.hex("#abcd"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StyleSpec(
                        Optional.empty(),
                        EnumSet.noneOf(TextDecoration.class),
                        Optional.of(ResourceLocation.fromNamespaceAndPath("", "default"))
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StyleSpec(
                        Optional.empty(),
                        EnumSet.noneOf(TextDecoration.class),
                        Optional.of(ResourceLocation.fromNamespaceAndPath("minecraft", ""))
                )
        );
    }

    private static ComponentSpec parentOf(ComponentSpec child) {
        return componentWithChildren("parent", List.of(child));
    }

    private static ComponentSpec componentWithChildren(String fallback, List<ComponentSpec> children) {
        return new ComponentSpec(
                Optional.empty(),
                fallback,
                Map.of(),
                StyleSpec.EMPTY,
                children
        );
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
