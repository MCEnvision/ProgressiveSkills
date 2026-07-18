package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.creator.CreatorCatalog;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.envisione.progressiveskills.common.pack.ContentPackLoaderTest.root;
import static com.envisione.progressiveskills.common.pack.ContentPackLoaderTest.write;
import static com.envisione.progressiveskills.common.pack.ContentPackLoaderTest.writePack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionTemplateExpansionTest {
    @TempDir
    Path temporary;

    @Test
    void typedDefinitionsReceiveBoundedInheritedTemplateFields() throws IOException {
        Path packs = temporary.resolve("packs");
        writePack(packs, "base", "base:core", "base", List.of(), 0);
        write(packs.resolve("base/templates/base_skill.toml"), """
                schema_version = 2
                [template]
                target_kind = "progressiveskills:skill"
                extends = []
                [template.fields]
                enabled = true
                max_level = 5
                overflow = "bank"
                negative_xp_policy = "deny"
                """);
        write(packs.resolve("base/skills/training.toml"), """
                schema_version = 2
                [skill]
                display = { fallback = "Training" }
                icon = { type = "item", value = "minecraft:book", fallback = "minecraft:barrier", alt = "Book" }
                templates = ["base:base_skill"]
                [curve]
                type = "linear"
                base = 10
                step = 5
                rounding = "ceil"
                """);

        StagingResult result = new ContentPackLoader().stage(
                List.of(root(packs)), AvailableEnvironment.empty());

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var snapshot = result.snapshot().orElseThrow();
        assertEquals(5, SkillCatalog.from(snapshot.canonicalIr()).skill(
                ResourceLocation.parse("base:training")).orElseThrow().curve().maxLevel());
        assertEquals(5L, CreatorCatalog.from(snapshot.canonicalIr()).resolvedTemplate(
                ResourceLocation.parse("base:base_skill")).integer("max_level").orElseThrow());
    }

    @Test
    void templateTargetMismatchFailsTheOwningDefinition() throws IOException {
        Path packs = temporary.resolve("mismatch");
        writePack(packs, "base", "base:core", "base", List.of(), 0);
        write(packs.resolve("base/templates/class_only.toml"), """
                schema_version = 2
                [template]
                target_kind = "progressiveskills:class"
                extends = []
                [template.fields]
                enabled = true
                """);
        write(packs.resolve("base/skills/training.toml"), """
                schema_version = 2
                [skill]
                display = { fallback = "Training" }
                icon = { type = "item", value = "minecraft:book", fallback = "minecraft:barrier", alt = "Book" }
                templates = ["base:class_only"]
                [curve]
                type = "flat"
                base = 10
                """);

        StagingResult result = new ContentPackLoader().stage(
                List.of(root(packs)), AvailableEnvironment.empty());

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(value ->
                value.message().contains(DefinitionKinds.CLASS.id().toString())
                        && value.message().contains(DefinitionKinds.SKILL.id().toString())));
    }
}
