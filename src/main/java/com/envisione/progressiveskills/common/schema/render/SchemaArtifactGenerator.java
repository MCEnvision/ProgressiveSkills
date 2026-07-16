package com.envisione.progressiveskills.common.schema.render;

import com.envisione.progressiveskills.common.schema.CoreSchemas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Supported deterministic entry point for regenerating Phase 2 schema artifacts. */
public final class SchemaArtifactGenerator {
    public static final String REFERENCE_FILE = "SCHEMA-V2.md";
    public static final String EDITOR_CATALOG_FILE = "schema-v2-editor.json";

    private SchemaArtifactGenerator() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected exactly one output-directory argument");
        }
        writeArtifacts(Path.of(arguments[0]));
    }

    public static void writeArtifacts(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        var registry = CoreSchemas.createRegistry();
        Files.writeString(
                outputDirectory.resolve(REFERENCE_FILE),
                SchemaArtifactRenderer.referenceMarkdown(registry),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                outputDirectory.resolve(EDITOR_CATALOG_FILE),
                SchemaArtifactRenderer.editorCatalogJson(registry),
                StandardCharsets.UTF_8
        );
    }
}
