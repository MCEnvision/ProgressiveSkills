package com.envisione.progressiveskills.common.schema.render;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.schema.CoreSchemas;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaArtifactRendererTest {
    @Test
    void generatedReferenceIsDeterministicAndContainsRequiredColumns() {
        var registry = CoreSchemas.createRegistry();
        var first = SchemaArtifactRenderer.referenceMarkdown(registry);
        var second = SchemaArtifactRenderer.referenceMarkdown(CoreSchemas.createRegistry());

        assertEquals(first, second);
        assertTrue(first.contains("| Key | Type | Allowed values | Default | Description | Example |"));
        assertTrue(first.contains("| Kind ID | Source directory |"));
        assertTrue(first.contains("| `progressiveskills:variable` | `variables` |"));
        assertTrue(first.contains("PS-ID-002"));
        for (var diagnostic : registry.diagnostics().descriptors()) {
            assertTrue(first.contains(
                    "<a id=\"" + diagnostic.code().value().toLowerCase(Locale.ROOT) + "\"></a>"
            ));
        }
        assertTrue(first.contains("internal runtime contracts implemented through Phase 6"));
        assertTrue(first.contains("Gameplay definition schemas arrive with their implementation phases."));
    }

    @Test
    void editorCatalogIsDeterministicValidJsonWithProjectionMetadata() {
        var first = SchemaArtifactRenderer.editorCatalogJson(CoreSchemas.createRegistry());
        var second = SchemaArtifactRenderer.editorCatalogJson(CoreSchemas.createRegistry());
        var root = JsonParser.parseString(first).getAsJsonObject();

        assertEquals(first, second);
        assertEquals(1, root.get("catalog_format_version").getAsInt());
        assertEquals(SchemaVersion.CURRENT.value(), root.get("schema_version").getAsInt());
        var kinds = root.getAsJsonArray("definition_kinds");
        assertEquals(DefinitionKinds.all().size(), kinds.size());
        for (int index = 0; index < kinds.size(); index++) {
            var actual = kinds.get(index).getAsJsonObject();
            var expected = DefinitionKinds.all().get(index);
            assertEquals(expected.id().toString(), actual.get("id").getAsString());
            assertEquals(expected.sourceDirectory(), actual.get("source_directory").getAsString());
        }
        assertEquals(25, root.getAsJsonArray("schemas").size());
        assertTrue(first.contains("\"projection\": \"server_only\""));

        var schemas = root.getAsJsonArray("schemas");
        var style = findSchema(schemas, "progressiveskills:style_spec");
        var bold = findField(style.getAsJsonArray("fields"), "bold");
        assertTrue(bold.get("default").isJsonPrimitive());
        assertFalse(bold.get("default").getAsBoolean());

        var component = findSchema(schemas, "progressiveskills:component_spec");
        assertTrue(findField(component.getAsJsonArray("fields"), "children")
                .get("default").isJsonArray());
        assertTrue(findField(component.getAsJsonArray("fields"), "style")
                .get("default").isJsonObject());

        var icon = findSchema(schemas, "progressiveskills:icon_spec");
        var narrationDefault = findField(icon.getAsJsonArray("fields"), "narration")
                .getAsJsonObject("default");
        assertEquals("alt", narrationDefault.get("$field").getAsString());
        assertFalse(findField(icon.getAsJsonArray("fields"), "value").get("required").getAsBoolean());
        assertEquals("exactly_one", icon.getAsJsonArray("constraints")
                .get(0).getAsJsonObject().get("type").getAsString());

        var header = findSchema(schemas, "progressiveskills:definition_header");
        assertEquals(3, header.getAsJsonArray("constraints").size());
        assertTrue(first.contains("\"type\": \"required_together\""));
        assertTrue(first.contains("\"trigger\": \"description\""));
    }

    private static com.google.gson.JsonObject findSchema(
            com.google.gson.JsonArray schemas,
            String id
    ) {
        for (var element : schemas) {
            var schema = element.getAsJsonObject();
            if (schema.get("id").getAsString().equals(id)) {
                return schema;
            }
        }
        throw new AssertionError("Missing schema " + id);
    }

    private static com.google.gson.JsonObject findField(
            com.google.gson.JsonArray fields,
            String path
    ) {
        for (var element : fields) {
            var field = element.getAsJsonObject();
            if (field.get("path").getAsString().equals(path)) {
                return field;
            }
        }
        throw new AssertionError("Missing field " + path);
    }
}
