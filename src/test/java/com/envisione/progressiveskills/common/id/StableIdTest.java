package com.envisione.progressiveskills.common.id;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableIdTest {
    private static final DefinitionKind SKILL = DefinitionKind.of("progressiveskills:skill", "skills");
    private static final DefinitionKind TREE = DefinitionKind.of("progressiveskills:tree", "trees");

    @Test
    void parsingRequiresAnExplicitNonblankNamespaceAndPath() {
        assertEquals("mypack:combat/physique", StableId.parse("mypack:combat/physique").toString());

        List<String> invalid = List.of(
                "physique",
                ":physique",
                "mypack:",
                "mypack:combat:physique",
                "MyPack:physique",
                "mypack:Combat",
                "mypack:/physique",
                "mypack:physique/",
                "mypack:combat//physique",
                "mypack:combat/../physique"
        );
        invalid.forEach(value -> assertThrows(IllegalArgumentException.class, () -> StableId.parse(value), value));
        assertTrue(StableId.tryParse("mypack:physique").isPresent());
        assertFalse(StableId.tryParse("physique").isPresent());
        assertFalse(StableId.tryParse(null).isPresent());
    }

    @Test
    void vanillaResourceLocationLoopholesAreClosedWhenRevalidating() {
        ResourceLocation emptyNamespace = ResourceLocation.fromNamespaceAndPath("", "physique");
        ResourceLocation emptyPath = ResourceLocation.fromNamespaceAndPath("mypack", "");

        assertThrows(IllegalArgumentException.class, () -> StableId.requireValid(emptyNamespace));
        assertThrows(IllegalArgumentException.class, () -> StableId.requireValid(emptyPath));
        assertThrows(
                IllegalArgumentException.class,
                () -> StableId.parse("mypack:" + "x".repeat(StableId.MAX_PATH_SEGMENT_LENGTH + 1))
        );
    }

    @Test
    void idsDeriveExactlyFromKindDirectoryAndTomlPath() {
        assertEquals(
                "mypack:combat/physique",
                StableId.derive(SKILL, "mypack", "skills/combat/physique.toml").toString()
        );
        assertEquals(
                "mypack:warrior_tree",
                StableId.derive(TREE, "mypack", "trees/warrior_tree.toml").toString()
        );
        assertEquals(
                "mypack:combat/physique",
                StableId.deriveAndMatch(
                        SKILL,
                        "mypack",
                        "skills/combat/physique.toml",
                        "mypack:combat/physique"
                ).toString()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> StableId.deriveAndMatch(
                        SKILL,
                        "mypack",
                        "skills/combat/physique.toml",
                        "mypack:combat/renamed"
                )
        );
    }

    @Test
    void derivationRejectsNoncanonicalOrEscapingSourcePaths() {
        List<String> invalid = List.of(
                "/skills/a.toml",
                "skills\\a.toml",
                "skills/../a.toml",
                "skills/./a.toml",
                "skills//a.toml",
                "trees/a.toml",
                "skills/a.TOML",
                "skills/.toml",
                "skills/a.json"
        );
        invalid.forEach(
                sourcePath -> assertThrows(
                        IllegalArgumentException.class,
                        () -> StableId.derive(SKILL, "mypack", sourcePath),
                        sourcePath
                )
        );
    }

    @Test
    void definitionKindsAreExtensibleStableValues() {
        DefinitionKind first = DefinitionKind.of("addon:reputation", "reputations");
        DefinitionKind sameIdentityDifferentMetadata = DefinitionKind.of("addon:reputation", "reputation_defs");

        assertEquals(first, sameIdentityDifferentMetadata);
        assertEquals(0, first.compareTo(sameIdentityDifferentMetadata));
        assertThrows(
                IllegalArgumentException.class,
                () -> DefinitionKind.of("addon:nested/kind", "reputations")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DefinitionKind.of("addon:reputation", "nested/reputations")
        );
    }

    @Test
    void definitionKeysUseNamespaceFirstOrderingRatherThanVanillaPathFirstOrdering() {
        DefinitionKey zetaA = DefinitionKey.parse(SKILL, "zeta:a");
        DefinitionKey alphaZ = DefinitionKey.parse(SKILL, "alpha:z");

        assertTrue(zetaA.id().compareTo(alphaZ.id()) < 0, "vanilla ordering compares the path first");
        assertTrue(zetaA.compareTo(alphaZ) > 0, "canonical ordering must compare namespaces first");
        assertEquals(List.of(alphaZ, zetaA), List.of(zetaA, alphaZ).stream().sorted().toList());
        assertThrows(IllegalArgumentException.class, () -> DefinitionKey.parse(SKILL, "unnamespaced"));
    }
}
