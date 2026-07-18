package com.envisione.progressiveskills.common.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.envisione.progressiveskills.common.pack.ContentPackLoaderTest.root;
import static com.envisione.progressiveskills.common.pack.ContentPackLoaderTest.write;
import static com.envisione.progressiveskills.common.pack.ContentPackLoaderTest.writePack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRegistryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dryRunIsNonMutatingAndPublishRejectsPostReviewChanges() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path definition = packs.resolve("base-pack/component_specs/greeting.toml");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(definition, "Hello");
        var service = service(packs);
        assertTrue(service.start().live().isPresent());
        assertEquals(1, service.live().generation());
        String originalDigest = service.live().snapshot().contentDigest();

        writeDefinition(definition, "Updated");
        StageAttempt dryRun = service.stageDryRun();
        assertTrue(dryRun.result().valid());
        assertEquals(1, dryRun.diff().orElseThrow().definitions(SemanticDiff.ChangeKind.MODIFIED));
        assertEquals(originalDigest, service.live().snapshot().contentDigest());

        writeDefinition(definition, "Changed after review");
        PublishResult rejected = service.publishStaged();
        assertFalse(rejected.published());
        assertEquals(originalDigest, service.live().snapshot().contentDigest());

        service.stageDryRun();
        PublishResult published = service.publishStaged();
        assertTrue(published.published());
        assertEquals(2, service.live().generation());
    }

    @Test
    void validationDoesNotArmPublishAndSourceOnlyPostReviewChangesAreRejected() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path definition = packs.resolve("base-pack/component_specs/greeting.toml");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(definition, "Hello");
        var service = service(packs);
        assertTrue(service.start().live().isPresent());

        writeDefinition(definition, "Updated");
        assertTrue(service.validate().valid());
        assertFalse(service.publishStaged().published());

        Files.writeString(definition, Files.readString(definition) + "\n# reviewed\n");
        StageAttempt staged = service.stageDryRun();
        assertTrue(staged.result().valid());
        Files.writeString(definition, Files.readString(definition) + "# changed after review\n");
        PublishResult rejected = service.publishStaged();

        assertFalse(rejected.published());
        assertTrue(rejected.message().contains("changed after dry-run"));
        assertEquals(1, service.live().generation());
    }

    @Test
    void replacementValidationExcludesThePublishedSourceWithoutMutatingIt() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path published = packs.resolve("base-pack");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(published.resolve("component_specs/greeting.toml"), "Published");
        var service = service(packs);
        assertTrue(service.start().live().isPresent());
        String publishedDigest = service.live().snapshot().contentDigest();

        Path preview = temporaryDirectory.resolve("preview");
        writePack(preview, "candidate", "base:core", "base", List.of(), 0);
        writeDefinition(preview.resolve("candidate/component_specs/greeting.toml"), "Candidate");
        StagingResult result = service.validateWithReplacement(
                published,
                new PackRoot(PackRootTier.STUDIO_OVERLAY, "preview", preview));

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        assertNotEquals(publishedDigest, result.snapshot().orElseThrow().contentDigest());
        assertEquals("Published", Files.readString(
                published.resolve("component_specs/greeting.toml")).lines()
                .filter(line -> line.startsWith("fallback")).findFirst().orElseThrow()
                .replace("fallback = ", "").replace("\"", ""));
        assertEquals(publishedDigest, service.live().snapshot().contentDigest());
    }

    @Test
    void invalidCurrentSourceRecoversVerifiedLastKnownGoodBundle() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path definition = packs.resolve("base-pack/component_specs/greeting.toml");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(definition, "Hello");
        DefinitionRegistryService first = service(packs);
        assertTrue(first.start().live().isPresent());
        String knownGoodDigest = first.live().snapshot().contentDigest();

        write(definition, "this is not toml = [");
        DefinitionRegistryService restarted = service(packs);
        StartupLoadResult result = restarted.start();

        assertTrue(result.recovered());
        assertTrue(result.live().isPresent());
        assertEquals(knownGoodDigest, restarted.live().snapshot().contentDigest());
        assertTrue(result.primaryResult().diagnostics().hasErrors());
    }

    @Test
    void storedBundleRoundTripsAndRejectsChecksumCorruption() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(packs.resolve("base-pack/component_specs/greeting.toml"), "Hello");
        PackSnapshot snapshot = new ContentPackLoader().stage(List.of(root(packs)), AvailableEnvironment.empty())
                .snapshot().orElseThrow();
        Path storePath = temporaryDirectory.resolve("store");
        var store = new LastKnownGoodStore(storePath);
        store.save(1, snapshot);

        StoredPackBundle loaded = store.loadCurrent().orElseThrow();
        assertEquals(snapshot.contentDigest(), loaded.contentDigest());
        assertEquals(snapshot.sourceBundle(), loaded.sourceBundle());
        assertEquals(snapshot.packSourceDigests(), loaded.lockMetadata().packSourceDigests());
        assertEquals(SemanticVersion.ENGINE_CURRENT, loaded.lockMetadata().engineVersion());
        String lock = Files.readString(storePath.resolve("progressiveskills.lock"));
        assertTrue(lock.contains("pack.0.id=base:core"));
        assertTrue(lock.contains("pack.0.version=1.0.0"));
        List<PackRoot> recoveredRoots = store.materialize(loaded, temporaryDirectory.resolve("materialized"));
        StagingResult recovered = new ContentPackLoader().stage(recoveredRoots, AvailableEnvironment.empty());
        assertEquals(snapshot.contentDigest(), recovered.snapshot().orElseThrow().contentDigest());

        Files.writeString(storePath.resolve("progressiveskills.sources"), "corrupt");
        assertThrows(IOException.class, store::loadCurrent);
    }

    @Test
    void corruptCurrentGenerationFallsBackToVerifiedPreviousGeneration() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path definition = packs.resolve("base-pack/component_specs/greeting.toml");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(definition, "First");
        DefinitionRegistryService service = service(packs);
        assertTrue(service.start().live().isPresent());
        String firstDigest = service.live().snapshot().contentDigest();

        writeDefinition(definition, "Second");
        service.stageDryRun();
        assertTrue(service.publishStaged().published());
        assertEquals(2, service.live().generation());

        Files.writeString(temporaryDirectory.resolve("store/progressiveskills.sources"), "corrupt");
        write(definition, "this is not toml = [");
        DefinitionRegistryService restarted = service(packs);
        StartupLoadResult recovered = restarted.start();

        assertTrue(recovered.recovered());
        assertEquals(1, restarted.live().generation());
        assertEquals(firstDigest, restarted.live().snapshot().contentDigest());
    }

    @Test
    void validPrimaryContentReplacesAnUnreadableJournal() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path definition = packs.resolve("base-pack/component_specs/greeting.toml");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        writeDefinition(definition, "Hello");
        DefinitionRegistryService first = service(packs);
        assertTrue(first.start().live().isPresent());

        Files.writeString(temporaryDirectory.resolve("store/progressiveskills.sources"), "corrupt");
        DefinitionRegistryService restarted = service(packs);
        StartupLoadResult result = restarted.start();

        assertTrue(result.live().isPresent());
        assertFalse(result.recovered());
        assertTrue(new LastKnownGoodStore(temporaryDirectory.resolve("store")).loadCurrent().isPresent());
    }

    @Test
    void recoveryRejectsAnEnvironmentThatDoesNotMatchThePublishedLock() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Path definition = packs.resolve("base-pack/component_specs/greeting.toml");
        writePack(packs, "base-pack", "base:core", "base", List.of(), 0);
        Path manifest = packs.resolve("base-pack/pack.toml");
        Files.writeString(manifest, Files.readString(manifest) + "optional_mods = [\"examplemod\"]\n");
        writeDefinition(definition, "Hello");
        var publishedEnvironment = new AvailableEnvironment(
                SemanticVersion.ENGINE_CURRENT,
                Map.of("examplemod", "1.0.0")
        );
        assertTrue(service(packs, publishedEnvironment).start().live().isPresent());

        write(definition, "this is not toml = [");
        var changedEnvironment = new AvailableEnvironment(
                SemanticVersion.ENGINE_CURRENT,
                Map.of("examplemod", "2.0.0")
        );

        assertThrows(IOException.class, () -> service(packs, changedEnvironment).start());
    }

    private DefinitionRegistryService service(Path packs) {
        return service(packs, AvailableEnvironment.empty());
    }

    private DefinitionRegistryService service(Path packs, AvailableEnvironment environment) {
        Path store = temporaryDirectory.resolve("store");
        return new DefinitionRegistryService(
                List.of(root(packs)),
                environment,
                new LastKnownGoodStore(store),
                temporaryDirectory.resolve("recovery")
        );
    }

    private static void writeDefinition(Path path, String fallback) throws IOException {
        write(path, """
                schema_version = 2
                [component_spec]
                id = "base:greeting"
                fallback = "%s"
                """.formatted(fallback));
    }
}
