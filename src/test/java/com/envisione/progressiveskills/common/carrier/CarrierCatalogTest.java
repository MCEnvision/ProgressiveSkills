package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.pack.AvailableEnvironment;
import com.envisione.progressiveskills.common.pack.ContentPackLoader;
import com.envisione.progressiveskills.common.pack.PackRoot;
import com.envisione.progressiveskills.common.pack.PackRootTier;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.StarterPackInstaller;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierCatalogTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void starterCatalogResolvesAllActionsAndMaterializesPinnedDigests() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        StarterPackInstaller.install(packs);
        var staged = stage(packs);
        assertTrue(staged.valid(), () -> staged.diagnostics().diagnostics().toString());
        var ir = staged.snapshot().orElseThrow().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);

        CarrierCatalog catalog = CarrierCatalog.from(ir, skills, trees);

        assertEquals(3, catalog.carriers().size());
        assertEquals(CarrierKind.TOME, catalog.carrier(id("progressiveskills:tome_of_physique"))
                .orElseThrow().carrier());
        assertEquals(64, catalog.behaviorDigest(id("progressiveskills:tome_of_physique"))
                .orElseThrow().length());
        assertEquals(
                catalog.behavior(id("progressiveskills:physique_level_token")).orElseThrow().digest(),
                catalog.behaviorDigest(id("progressiveskills:physique_level_token")).orElseThrow()
        );
    }

    @Test
    void stagingRejectsMissingSkillCurrencyAndTreeReferences() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path pack = StarterPackInstaller.install(packs);
        Path tome = pack.resolve("items/tome_of_physique.toml");
        Path level = pack.resolve("items/physique_level_token.toml");
        Path respec = pack.resolve("items/physique_respec_token.toml");
        String tomeSource = Files.readString(tome);
        String levelSource = Files.readString(level);
        String respecSource = Files.readString(respec);

        Files.writeString(tome, tomeSource.replace(
                "skill = \"progressiveskills:physique\"",
                "skill = \"progressiveskills:missing\""
        ));
        assertProblem(packs, "references missing skill progressiveskills:missing");

        Files.writeString(tome, tomeSource.replace(
                "type = \"xp\"\nskill = \"progressiveskills:physique\"\namount = 500",
                "type = \"currency\"\ncurrency = \"progressiveskills:missing\"\namount = 500"
        ));
        assertProblem(packs, "references missing currency progressiveskills:missing");

        Files.writeString(tome, tomeSource);
        Files.writeString(level, levelSource);
        Files.writeString(respec, respecSource.replace(
                "tree = \"progressiveskills:physique_training\"",
                "tree = \"progressiveskills:missing\""
        ));
        assertProblem(packs, "references missing tree progressiveskills:missing");
    }

    private static void assertProblem(Path packs, String expected) {
        var result = stage(packs);
        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(problem ->
                problem.message().contains(expected)), () -> result.diagnostics().diagnostics().toString());
    }

    private static com.envisione.progressiveskills.common.pack.StagingResult stage(Path packs) {
        return new ContentPackLoader().stage(
                List.of(new PackRoot(PackRootTier.GLOBAL_CONFIG, "test", packs)),
                AvailableEnvironment.empty()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
