package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPackLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validPackCompilesSharedPrimitiveAndIgnoresSourceOnlyChangesInDigest() throws IOException {
        Path firstRoot = temporaryDirectory.resolve("first");
        writePack(firstRoot, "base-pack", "base:core", "base", List.of(), 0);
        write(firstRoot.resolve("base-pack/component_specs/greeting.toml"), """
                schema_version = 2
                [component_spec]
                id = "base:greeting"
                key = "text.base.greeting"
                fallback = "Hello"
                """);
        StagingResult first = loader(firstRoot).stage(List.of(root(firstRoot)), AvailableEnvironment.empty());

        assertTrue(first.valid(), () -> first.diagnostics().diagnostics().toString());
        var key = DefinitionKey.parse(DefinitionKinds.COMPONENT_SPEC, "base:greeting");
        CanonicalValue value = first.snapshot().orElseThrow().canonicalIr().definitions().get(key).fields()
                .fields().get("value");
        assertTrue(value instanceof CanonicalValue.ComponentValue);

        Path secondRoot = temporaryDirectory.resolve("second");
        writePack(secondRoot, "base-pack", "base:core", "base", List.of(), 0);
        write(secondRoot.resolve("base-pack/component_specs/greeting.toml"), """
                # Comments and field order are source-only.
                schema_version = 2
                [component_spec]
                fallback = "Hello"
                key = "text.base.greeting"
                id = "base:greeting"
                """);
        StagingResult second = loader(secondRoot).stage(List.of(root(secondRoot)), AvailableEnvironment.empty());
        assertTrue(second.valid());
        assertEquals(first.snapshot().orElseThrow().contentDigest(), second.snapshot().orElseThrow().contentDigest());
        assertFalse(first.snapshot().orElseThrow().sourceBundle().equals(second.snapshot().orElseThrow().sourceBundle()));
        assertNotEquals(first.snapshot().orElseThrow().sourceBundle().digest(),
                second.snapshot().orElseThrow().sourceBundle().digest());
    }

    @Test
    void dependencyOrderWinsDeterministicTieBeforeMerge() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        writePack(packs, "z-base", "shared:zbase", "shared", List.of(), 0);
        write(packs.resolve("z-base/component_specs/greeting.toml"), """
                schema_version = 2
                [component_spec]
                fallback = "Base"
                """);
        writePack(packs, "a-overlay", "shared:aoverlay", "shared", List.of("shared:zbase@>=1.0.0"), 0);
        write(packs.resolve("a-overlay/component_specs/greeting.toml"), """
                schema_version = 2
                merge_intent = "merge"
                [component_spec]
                fallback = "Overlay"
                """);

        StagingResult result = loader(packs).stage(List.of(root(packs)), AvailableEnvironment.empty());

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var key = DefinitionKey.parse(DefinitionKinds.COMPONENT_SPEC, "shared:greeting");
        var component = (CanonicalValue.ComponentValue) result.snapshot().orElseThrow().canonicalIr()
                .definitions().get(key).fields().fields().get("value");
        assertEquals("Overlay", component.value().fallback());
    }

    @Test
    void dependencyCannotContradictRootPrecedence() throws IOException {
        Path global = temporaryDirectory.resolve("global");
        Path world = temporaryDirectory.resolve("world");
        writePack(global, "owner", "shared:owner", "shared", List.of("shared:later"), 0);
        writePack(world, "later", "shared:later", "shared", List.of(), 0);

        StagingResult result = new ContentPackLoader().stage(List.of(
                new PackRoot(PackRootTier.GLOBAL_CONFIG, "global", global),
                new PackRoot(PackRootTier.WORLD_OVERLAY, "world", world)
        ), AvailableEnvironment.empty());

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.descriptor().code().equals(CoreDiagnostics.PACK_PRECEDENCE_CONFLICT)));
    }

    @Test
    void patchDisableAliasAndFieldProvenanceRemainExplicit() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        writePack(packs, "base", "shared:base", "shared", List.of(), 0);
        write(packs.resolve("base/component_specs/greeting.toml"), """
                schema_version = 2
                [component_spec]
                fallback = "Base"
                """);
        write(packs.resolve("base/replacements.toml"), """
                schema_version = 2
                [[replacements]]
                kind = "progressiveskills:component_spec"
                old_id = "shared:old_greeting"
                new_id = "shared:greeting"
                """);
        writePack(packs, "patch", "shared:patch", "shared", List.of("shared:base"), 10);
        write(packs.resolve("patch/component_specs/greeting.toml"), """
                schema_version = 2
                merge_intent = "patch"
                [[patches]]
                op = "set"
                path = "fallback"
                value = "Patched"
                """);
        writePack(packs, "disable", "shared:disable", "shared", List.of("shared:patch"), 20);
        write(packs.resolve("disable/component_specs/greeting.toml"), """
                schema_version = 2
                merge_intent = "disable"
                """);

        StagingResult result = loader(packs).stage(List.of(root(packs)), AvailableEnvironment.empty());

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var key = DefinitionKey.parse(DefinitionKinds.COMPONENT_SPEC, "shared:greeting");
        var oldKey = DefinitionKey.parse(DefinitionKinds.COMPONENT_SPEC, "shared:old_greeting");
        var definition = result.snapshot().orElseThrow().canonicalIr().definitions().get(key);
        var component = (CanonicalValue.ComponentValue) definition.fields().fields().get("value");
        assertEquals("Patched", component.value().fallback());
        assertTrue(result.snapshot().orElseThrow().disabledDefinitions().contains(key));
        assertEquals(key, result.snapshot().orElseThrow().canonicalIr().aliases().resolveTerminal(oldKey));
        assertEquals("component_specs/greeting.toml",
                definition.sourceMap().find("fallback").orElseThrow().provenance().sourcePath());
        assertEquals("shared:patch",
                definition.sourceMap().find("fallback").orElseThrow().provenance().packId().toString());
        assertEquals(5, definition.sourceMap().find("fallback").orElseThrow().span().start().line());
    }

    @Test
    void manifestOptionalMetadataCompilesAndUnknownPolicyModesFailClosed() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        write(packs.resolve("metadata/pack.toml"), """
                schema_version = 2
                [pack]
                id = "meta:core"
                namespace = "meta"
                name = { fallback = "Metadata" }
                content_version = "1.2.3"
                engine = ">=1.0.0 <2.0.0"
                authors = ["Tests"]
                license = "Test-Only"
                default_theme = "meta:theme"
                default_layout = "meta:layout"
                homepage = "https://example.invalid/home"
                source = "https://example.invalid/source"
                description = "Metadata fixture"
                changelog_url = "https://example.invalid/changelog"
                feature_flags = ["fixture"]
                exported_asset_pack_id = "meta:assets"
                trusted_scripts = true
                [policies]
                unknown_field = "error"
                """);
        StagingResult valid = loader(packs).stage(List.of(root(packs)), AvailableEnvironment.empty());
        assertTrue(valid.valid(), () -> valid.diagnostics().diagnostics().toString());
        assertTrue(valid.snapshot().orElseThrow().manifests().get(
                com.envisione.progressiveskills.common.id.StableId.parse("meta:core")
        ).trustedScripts());

        Files.writeString(packs.resolve("metadata/pack.toml"), Files.readString(packs.resolve("metadata/pack.toml"))
                .replace("unknown_field = \"error\"", "unknown_field = \"warn\""));
        StagingResult invalid = loader(packs).stage(List.of(root(packs)), AvailableEnvironment.empty());
        assertFalse(invalid.valid());
    }

    @Test
    void symbolicPackEntriesAreRejectedBeforeCompilation() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        writePack(packs, "base", "base:core", "base", List.of(), 0);
        Path outside = temporaryDirectory.resolve("outside.toml");
        write(outside, "schema_version = 2");
        Files.createSymbolicLink(packs.resolve("base/component_specs"), outside);

        StagingResult result = loader(packs).stage(List.of(root(packs)), AvailableEnvironment.empty());

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.descriptor().code().equals(CoreDiagnostics.SOURCE_LIMIT_EXCEEDED)));
    }

    @Test
    void sameIdAcrossTypedKindsProducesANonBlockingReadabilityWarning() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        writePack(packs, "base", "base:core", "base", List.of(), 0);
        write(packs.resolve("base/component_specs/shared.toml"), """
                schema_version = 2
                [component_spec]
                fallback = "Shared"
                """);
        write(packs.resolve("base/icon_specs/shared.toml"), """
                schema_version = 2
                [icon_spec]
                type = "item"
                value = "minecraft:stone"
                fallback = "minecraft:barrier"
                alt = "Shared icon"
                """);

        StagingResult result = loader(packs).stage(List.of(root(packs)), AvailableEnvironment.empty());

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        assertEquals(2, result.snapshot().orElseThrow().canonicalIr().definitions().size());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.descriptor().code().equals(CoreDiagnostics.CROSS_KIND_ID_WARNING)
                        && diagnostic.severity()
                        == com.envisione.progressiveskills.common.diagnostic.DiagnosticSeverity.WARNING));
    }

    @Test
    void dependenciesAndExplicitMergeProduceOneDeterministicDefinition() throws IOException {
        Path global = temporaryDirectory.resolve("global");
        Path world = temporaryDirectory.resolve("world");
        writePack(global, "base-pack", "base:core", "base", List.of(), 0);
        write(global.resolve("base-pack/component_specs/greeting.toml"), """
                schema_version = 2
                [component_spec]
                id = "base:greeting"
                key = "text.base.greeting"
                fallback = "Hello"
                """);
        writePack(world, "overlay-pack", "base:overlay", "base", List.of("base:core@>=1.0.0"), 100);
        write(world.resolve("overlay-pack/component_specs/greeting.toml"), """
                schema_version = 2
                merge_intent = "merge"
                [component_spec]
                id = "base:greeting"
                fallback = "Welcome"
                """);

        StagingResult result = new ContentPackLoader().stage(
                List.of(
                        new PackRoot(PackRootTier.GLOBAL_CONFIG, "global", global),
                        new PackRoot(PackRootTier.WORLD_OVERLAY, "world", world)
                ),
                AvailableEnvironment.empty()
        );

        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        assertEquals(2, result.snapshot().orElseThrow().manifests().size());
        assertEquals(1, result.snapshot().orElseThrow().canonicalIr().definitions().size());
        var key = DefinitionKey.parse(DefinitionKinds.COMPONENT_SPEC, "base:greeting");
        var component = (CanonicalValue.ComponentValue) result.snapshot().orElseThrow().canonicalIr()
                .definitions().get(key).fields().fields().get("value");
        assertEquals("Welcome", component.value().fallback());
        assertEquals("text.base.greeting", component.value().localizationKey().orElseThrow());
    }

    @Test
    void invalidIdsMissingDependenciesAndUnavailableSchemasAreReportedWithoutPartialSnapshot() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        writePack(root, "bad-pack", "bad:core", "bad", List.of("missing:base@>=1.0.0"), 0);
        write(root.resolve("bad-pack/skills/wrong.toml"), """
                schema_version = 2
                [skill]
                id = "bad:not_the_path"
                max_level = 10
                """);

        StagingResult result = loader(root).stage(List.of(root(root)), AvailableEnvironment.empty());

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.descriptor().code().equals(CoreDiagnostics.MISSING_PACK_DEPENDENCY)));
        assertTrue(result.diagnostics().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.descriptor().code().equals(CoreDiagnostics.INVALID_TOML)));
    }

    @Test
    void plannedButUnavailableGameplaySchemasFailClosed() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        writePack(root, "future-pack", "future:core", "future", List.of(), 0);
        write(root.resolve("future-pack/abilities/miner.toml"), """
                schema_version = 2
                [ability]
                id = "future:miner"
                """);

        StagingResult result = loader(root).stage(List.of(root(root)), AvailableEnvironment.empty());

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.descriptor().code().equals(CoreDiagnostics.UNSUPPORTED_DEFINITION_SCHEMA)));
    }

    @Test
    void overflowingSkillCurveProducesAStagingDiagnosticInsteadOfEscaping() throws IOException {
        Path root = temporaryDirectory.resolve("packs");
        writePack(root, "curve-pack", "curve:core", "curve", List.of(), 0);
        write(root.resolve("curve-pack/skills/overflow.toml"), """
                schema_version = 2
                [skill]
                display = { fallback = "Overflow" }
                icon = { type = "item", value = "minecraft:stone", fallback = "minecraft:barrier", alt = "Stone" }
                max_level = 2
                [curve]
                type = "flat"
                base = 9223372036854
                """);

        StagingResult result = loader(root).stage(List.of(root(root)), AvailableEnvironment.empty());

        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.descriptor().code().equals(CoreDiagnostics.INVALID_TOML)));
    }

    static ContentPackLoader loader(Path ignored) {
        return new ContentPackLoader();
    }

    static PackRoot root(Path path) {
        return new PackRoot(PackRootTier.GLOBAL_CONFIG, "test", path);
    }

    static void writePack(
            Path root,
            String directory,
            String id,
            String namespace,
            List<String> requiredPacks,
            int priority
    ) throws IOException {
        String dependencies = requiredPacks.stream().map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        write(root.resolve(directory + "/pack.toml"), """
                schema_version = 2
                [pack]
                id = "%s"
                namespace = "%s"
                name = { fallback = "%s" }
                content_version = "1.0.0"
                engine = ">=1.0.0 <2.0.0"
                authors = ["Tests"]
                license = "Test-Only"
                priority = %d
                default_locale = "en_us"
                [dependencies]
                required_packs = [%s]
                """.formatted(id, namespace, id, priority, dependencies));
    }

    static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
