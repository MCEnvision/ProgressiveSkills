package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.network.NetworkLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPackProjectionStagingTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stagingRejectsAProjectionAboveTheTotalDefinitionLimit() throws IOException {
        Path root = temporaryDirectory.resolve("definition_count");
        writeManifest(root);
        for (int index = 0; index <= NetworkLimits.MAX_DEFINITIONS; index++) {
            write(root.resolve("projection/component_specs/value_" + index + ".toml"), """
                    schema_version = 2
                    [component_spec]
                    fallback = "Value"
                    """);
        }

        StagingResult result = stage(root);

        assertProjectionLimitFailure(result, "Definition projection exceeds capacity");
    }

    @Test
    void stagingRejectsAProjectionAboveTheEncodedByteLimit() throws IOException {
        Path root = temporaryDirectory.resolve("encoded_bytes");
        writeManifest(root);
        write(root.resolve("projection/currencies/points.toml"), """
                schema_version = 2
                [currency]
                display = "Points"
                icon = { type = "item", value = "minecraft:stone", fallback = "minecraft:barrier", alt = "Points" }
                minimum = 0
                maximum = 100
                initial = 0
                scope = "character"
                """);
        String text = "x".repeat(2_048);
        for (int tree = 0; tree < 4; tree++) {
            var source = new StringBuilder();
            source.append("""
                    schema_version = 2
                    [tree]
                    display = "Tree"
                    icon = { type = "item", value = "minecraft:stone", fallback = "minecraft:barrier", alt = "Tree" }
                    enabled = true
                    scope = "global"
                    currency = "projection:points"
                    dependency_policy = "cascade_refund"
                    """);
            for (int node = 0; node < 64; node++) {
                source.append("""

                        [[nodes]]
                        id = "projection:tree_%d/node_%d"
                        display = { fallback = "%s" }
                        description = { fallback = "%s" }
                        icon = { type = "item", value = "minecraft:stone", fallback = "minecraft:barrier", alt = { fallback = "%s" }, narration = { fallback = "%s" } }
                        cost = 1
                        row = %d
                        col = 0
                        requires = []
                        requires_any = []
                        """.formatted(tree, node, text, text, text, text, node));
            }
            write(root.resolve("projection/trees/tree_" + tree + ".toml"), source.toString());
        }

        StagingResult result = stage(root);

        assertProjectionLimitFailure(result, "definition projection exceeds "
                + NetworkLimits.MAX_DEFINITION_BYTES + " bytes");
    }

    private static void assertProjectionLimitFailure(StagingResult result, String message) {
        assertFalse(result.valid());
        assertTrue(result.diagnostics().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.descriptor().code().equals(CoreDiagnostics.SOURCE_LIMIT_EXCEEDED)
                        && diagnostic.message().contains(message)),
                () -> result.diagnostics().diagnostics().toString());
    }

    private static StagingResult stage(Path root) {
        return new ContentPackLoader().stage(
                List.of(new PackRoot(PackRootTier.GLOBAL_CONFIG, "test", root)),
                AvailableEnvironment.empty()
        );
    }

    private static void writeManifest(Path root) throws IOException {
        write(root.resolve("projection/pack.toml"), """
                schema_version = 2
                [pack]
                id = "projection:pack"
                namespace = "projection"
                name = { fallback = "Projection" }
                content_version = "1.0.0"
                engine = ">=1.0.0 <2.0.0"
                authors = ["Tests"]
                license = "Test Only"
                priority = 0
                default_locale = "en_us"
                [dependencies]
                required_packs = []
                """);
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
