package com.envisione.progressiveskills.server.pack;

import com.envisione.progressiveskills.common.rule.BlockOrigin;
import com.envisione.progressiveskills.common.rule.RuleCatalog;
import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.requirement.RequirementDependency;
import com.envisione.progressiveskills.common.requirement.RequirementExpression;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterPackInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installSeedsOnceWithoutOverwritingOperatorEdits() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(root);
        Path manifest = pack.resolve("pack.toml");
        assertTrue(Files.readString(manifest).contains("progressiveskills:core"));
        assertTrue(Files.readString(pack.resolve("currencies/global_points.toml"))
                .contains("progressiveskills:global_points"));
        assertTrue(Files.readString(pack.resolve("skills/physique.toml"))
                .contains("progressiveskills:physique"));
        assertTrue(Files.readString(pack.resolve("rules/physique_stone_training.toml"))
                .contains("progressiveskills:block_break"));
        assertTrue(Files.readString(pack.resolve("rules/physique_stone_training.toml"))
                .contains("allowed_block_origins = [\"natural\", \"creative_placed\"]"));
        assertTrue(Files.readString(pack.resolve("rules/physique_stone_training.toml"))
                .contains("type = \"skill_level\""));
        assertTrue(Files.readString(pack.resolve("rules/physique_stone_training.toml"))
                .contains("rounding = \"floor\""));
        assertTrue(Files.readString(pack.resolve("rules/physique_first_log.toml"))
                .contains("first_time = true"));

        Files.writeString(manifest, "operator-owned");
        StarterPackInstaller.install(root);

        assertEquals("operator-owned", Files.readString(manifest));
    }

    @Test
    void installRejectsASymbolicStarterDirectoryBeforeWriting() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(root);
        Files.createDirectories(outside);
        Files.createSymbolicLink(root.resolve("progressiveskills-core"), outside);

        assertThrows(IOException.class, () -> StarterPackInstaller.install(root));
        try (var entries = Files.list(outside)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    @Test
    void installedStarterPackCompilesThePhysiqueVerticalSlice() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        StarterPackInstaller.install(root);

        var result = new com.envisione.progressiveskills.common.pack.ContentPackLoader().stage(
                List.of(new com.envisione.progressiveskills.common.pack.PackRoot(
                        com.envisione.progressiveskills.common.pack.PackRootTier.GLOBAL_CONFIG,
                        "test",
                        root
                )),
                com.envisione.progressiveskills.common.pack.AvailableEnvironment.empty()
        );

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var catalog = com.envisione.progressiveskills.common.skill.SkillCatalog.from(
                result.snapshot().orElseThrow().canonicalIr()
        );
        assertTrue(catalog.skill(net.minecraft.resources.ResourceLocation.parse(
                "progressiveskills:physique"
        )).isPresent());
        assertTrue(catalog.currency(net.minecraft.resources.ResourceLocation.parse(
                "progressiveskills:global_points"
        )).isPresent());
        var rules = com.envisione.progressiveskills.common.rule.RuleCatalog.from(
                result.snapshot().orElseThrow().canonicalIr(), catalog
        );
        assertEquals(2, rules.rules().size());
        assertEquals(2, rules.requirementIndex().edgeCount());
        assertEquals(1, rules.requirementIndex().consumers(new RequirementDependency(
                RequirementDependency.Kind.SKILL_LEVEL,
                ResourceLocation.parse("progressiveskills:physique")
        )).size());
        var stoneRule = rules.rule(ResourceLocation.parse(
                "progressiveskills:physique_stone_training"
        )).orElseThrow();
        assertEquals(ExpressionRounding.FLOOR, stoneRule.rounding());
        assertEquals(2, ((RequirementExpression.All) stoneRule.requirements()).children().size());
        var routes = com.envisione.progressiveskills.server.rule.BlockRuleTable.compile(rules);
        assertEquals(1, routes.match(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).size());
        assertTrue(routes.match(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()).isEmpty());
    }

    @Test
    void blockOriginListIsCustomizableAndOmissionRetainsTheSafeDefault() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(root);
        Path stone = pack.resolve("rules/physique_stone_training.toml");
        String starter = Files.readString(stone);

        Files.writeString(stone, starter.replace(
                "[\"natural\", \"creative_placed\"]",
                "[\"survival_placed\"]"
        ));
        assertEquals(
                Set.of(BlockOrigin.SURVIVAL_PLACED),
                stoneRule(root).antiExploit().allowedBlockOrigins()
        );

        Files.writeString(stone, starter.replace(
                "allowed_block_origins = [\"natural\", \"creative_placed\"]\n",
                ""
        ));
        assertEquals(
                Set.of(BlockOrigin.NATURAL, BlockOrigin.CREATIVE_PLACED),
                stoneRule(root).antiExploit().allowedBlockOrigins()
        );
    }

    @Test
    void directRequirementSurfaceRejectsUnsupportedSubjectsFormulasAndReferences() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(root);
        Path stone = pack.resolve("rules/physique_stone_training.toml");
        String starter = Files.readString(stone);

        Files.writeString(stone, starter.replace("subject = \"actor\"", "subject = \"target\""));
        assertFalse(stage(root).valid());

        Files.writeString(stone, starter.replace(
                "amount_formula = \"rule_amount\"",
                "amount_formula = \"base * 2\""
        ));
        assertFalse(stage(root).valid());

        Files.writeString(stone, starter.replace(
                "missing = false, skill = \"progressiveskills:physique\", op",
                "missing = false, skill = \"progressiveskills:missing\", op"
        ));
        var staged = stage(root);
        assertFalse(staged.valid());
        assertTrue(staged.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("requirement references missing skill")
        ));
    }

    @Test
    void directRequirementRangesShareOneIndexedDependency() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(root);
        Path stone = pack.resolve("rules/physique_stone_training.toml");
        String starter = Files.readString(stone);

        Files.writeString(stone, starter.replace(
                "{ type = \"skill_level\", subject = \"actor\", missing = false, skill = \"progressiveskills:physique\", op = \">=\", value = 0 },",
                "{ type = \"skill_level\", subject = \"actor\", missing = false, skill = \"progressiveskills:physique\", op = \">=\", value = 0 },\n"
                        + "    { type = \"skill_level\", subject = \"actor\", missing = false, skill = \"progressiveskills:physique\", op = \"<\", value = 100 },"
        ));

        var result = stage(root);
        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var skills = com.envisione.progressiveskills.common.skill.SkillCatalog.from(
                result.snapshot().orElseThrow().canonicalIr()
        );
        RuleCatalog rules = RuleCatalog.from(result.snapshot().orElseThrow().canonicalIr(), skills);
        var stoneRule = rules.rule(ResourceLocation.parse(
                "progressiveskills:physique_stone_training"
        )).orElseThrow();
        assertEquals(3, ((RequirementExpression.All) stoneRule.requirements()).children().size());
        assertEquals(2, rules.requirementIndex().edgeCount());
    }

    private static com.envisione.progressiveskills.common.rule.RuleDefinition stoneRule(Path root) {
        var result = stage(root);
        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var skills = com.envisione.progressiveskills.common.skill.SkillCatalog.from(
                result.snapshot().orElseThrow().canonicalIr()
        );
        RuleCatalog rules = RuleCatalog.from(result.snapshot().orElseThrow().canonicalIr(), skills);
        return rules.rule(ResourceLocation.parse("progressiveskills:physique_stone_training")).orElseThrow();
    }

    private static com.envisione.progressiveskills.common.pack.StagingResult stage(Path root) {
        return new com.envisione.progressiveskills.common.pack.ContentPackLoader().stage(
                List.of(new com.envisione.progressiveskills.common.pack.PackRoot(
                        com.envisione.progressiveskills.common.pack.PackRootTier.GLOBAL_CONFIG,
                        "test",
                        root
                )),
                com.envisione.progressiveskills.common.pack.AvailableEnvironment.empty()
        );
    }
}
