package com.envisione.progressiveskills.common.schema.render;

import com.envisione.progressiveskills.common.diagnostic.DiagnosticDescriptor;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.schema.FieldDescriptor;
import com.envisione.progressiveskills.common.schema.SchemaConstraint;
import com.envisione.progressiveskills.common.schema.SchemaDefaultValue;
import com.envisione.progressiveskills.common.schema.SchemaDescriptor;
import com.envisione.progressiveskills.common.schema.SchemaRegistry;

import java.util.Locale;
import java.util.Objects;

/** Deterministic renderers for checked-in reference and editor metadata. */
public final class SchemaArtifactRenderer {
    private SchemaArtifactRenderer() {
    }

    public static String referenceMarkdown(SchemaRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        var output = new StringBuilder();
        output.append("# ProgressiveSkills Schema Reference v")
                .append(schemaVersion(registry)).append("\n\n")
                .append("> Generated from `CoreSchemas`; edit the registry metadata, then regenerate this file.\n\n")
                .append("Schema v2 includes the shared immutable IR, authoring schemas, and internal runtime contracts implemented through Phase 4. Gameplay definition schemas arrive with their implementation phases.\n\n");

        output.append("## Definition-kind catalog\n\n")
                .append("| Kind ID | Source directory |\n")
                .append("|---|---|\n");
        for (var kind : registry.definitionKinds()) {
            appendDefinitionKind(output, kind);
        }
        output.append('\n');

        for (var schema : registry.schemas()) {
            appendSchema(output, schema);
        }

        output.append("## Diagnostic catalog\n\n");
        for (var diagnostic : registry.diagnostics().descriptors()) {
            appendDiagnostic(output, diagnostic);
        }
        if (!registry.diagnostics().descriptors().isEmpty()) {
            output.setLength(output.length() - 1);
        }
        return output.toString();
    }

    public static String editorCatalogJson(SchemaRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        var output = new StringBuilder();
        output.append("{\n")
                .append("  \"catalog_format_version\": 1,\n")
                .append("  \"schema_version\": ").append(schemaVersion(registry)).append(",\n")
                .append("  \"definition_kinds\": [\n");
        var definitionKinds = registry.definitionKinds().stream().toList();
        for (int kindIndex = 0; kindIndex < definitionKinds.size(); kindIndex++) {
            appendDefinitionKindJson(output, definitionKinds.get(kindIndex));
            output.append(kindIndex + 1 < definitionKinds.size() ? ",\n" : "\n");
        }
        output.append("  ],\n")
                .append("  \"schemas\": [\n");
        var schemas = registry.schemas().stream().toList();
        for (int schemaIndex = 0; schemaIndex < schemas.size(); schemaIndex++) {
            appendSchemaJson(output, schemas.get(schemaIndex));
            output.append(schemaIndex + 1 < schemas.size() ? ",\n" : "\n");
        }
        output.append("  ],\n")
                .append("  \"diagnostics\": [\n");
        var diagnostics = registry.diagnostics().descriptors().stream().toList();
        for (int diagnosticIndex = 0; diagnosticIndex < diagnostics.size(); diagnosticIndex++) {
            appendDiagnosticJson(output, diagnostics.get(diagnosticIndex));
            output.append(diagnosticIndex + 1 < diagnostics.size() ? ",\n" : "\n");
        }
        output.append("  ]\n")
                .append("}\n");
        return output.toString();
    }

    private static void appendDefinitionKind(StringBuilder output, DefinitionKind kind) {
        output.append("| `").append(kind.id()).append("` | `")
                .append(kind.sourceDirectory()).append("` |\n");
    }

    private static void appendDefinitionKindJson(StringBuilder output, DefinitionKind kind) {
        output.append("    {\n")
                .append("      \"id\": ").append(json(kind.id().toString())).append(",\n")
                .append("      \"source_directory\": ").append(json(kind.sourceDirectory())).append("\n")
                .append("    }");
    }

    private static void appendSchema(StringBuilder output, SchemaDescriptor schema) {
        output.append("## ").append(schema.title()).append("\n\n")
                .append("- Schema ID: `").append(schema.id()).append("`\n")
                .append("- Version: `").append(schema.currentVersion()).append("`\n")
                .append("- Audience: `").append(schema.audience().metadataName()).append("`\n\n")
                .append(schema.description()).append("\n\n")
                .append("| Key | Type | Allowed values | Default | Description | Example | Diagnostic | Editor | Projection | Diff |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (var field : schema.fields()) {
            output.append("| `").append(field.path()).append(field.required() ? "` (required)" : "`")
                    .append(" | `").append(field.type().authoringName()).append("`")
                    .append(" | ").append(field.allowedValues().isEmpty()
                            ? "—"
                            : markdownValue(String.join(" | ", field.allowedValues())))
                    .append(" | ").append(markdownValue(field.defaultValue()
                            .map(SchemaDefaultValue::displayValue)
                            .orElse("—")))
                    .append(" | ").append(markdown(field.description()))
                    .append(" | ").append(markdownValue(field.example()))
                    .append(" | `").append(field.diagnosticCode()).append("`")
                    .append(" | `").append(field.editor().widget().metadataName()).append("`")
                    .append(" | `").append(field.projection().metadataName()).append("`")
                    .append(" | `").append(field.diffPolicy().metadataName()).append("` |\n");
        }
        output.append('\n');
        if (!schema.constraints().isEmpty()) {
            output.append("Cross-field constraints:\n\n");
            for (var constraint : schema.constraints()) {
                output.append("- `").append(constraint.kind().metadataName()).append("`");
                constraint.triggerField().ifPresent(trigger -> output.append(" from `").append(trigger).append("`"));
                output.append(" → ")
                        .append(constraint.fields().stream()
                                .map(field -> "`" + field + "`")
                                .collect(java.util.stream.Collectors.joining(", ")))
                        .append(" (`").append(constraint.diagnosticCode()).append("`): ")
                        .append(markdown(constraint.description())).append("\n");
            }
            output.append('\n');
        }
    }

    private static void appendDiagnostic(StringBuilder output, DiagnosticDescriptor diagnostic) {
        output.append("<a id=\"").append(diagnostic.code().value().toLowerCase(Locale.ROOT))
                .append("\"></a>\n\n")
                .append("### ").append(diagnostic.code()).append(" — ").append(diagnostic.title()).append("\n\n")
                .append("- Default severity: `")
                .append(diagnostic.defaultSeverity().name().toLowerCase(Locale.ROOT)).append("`\n")
                .append("- Suppressible: `").append(diagnostic.suppressible()).append("`\n")
                .append("- Why it matters: ").append(diagnostic.whyItMatters()).append("\n")
                .append("- Suggested fix: ").append(diagnostic.suggestedFix()).append("\n\n");
    }

    private static void appendSchemaJson(StringBuilder output, SchemaDescriptor schema) {
        output.append("    {\n")
                .append("      \"id\": ").append(json(schema.id().toString())).append(",\n")
                .append("      \"version\": ").append(schema.currentVersion()).append(",\n")
                .append("      \"audience\": ").append(json(schema.audience().metadataName())).append(",\n")
                .append("      \"title\": ").append(json(schema.title())).append(",\n")
                .append("      \"description\": ").append(json(schema.description())).append(",\n")
                .append("      \"fields\": [\n");
        for (int fieldIndex = 0; fieldIndex < schema.fields().size(); fieldIndex++) {
            appendFieldJson(output, schema.fields().get(fieldIndex));
            output.append(fieldIndex + 1 < schema.fields().size() ? ",\n" : "\n");
        }
        output.append("      ],\n")
                .append("      \"constraints\": [\n");
        for (int constraintIndex = 0; constraintIndex < schema.constraints().size(); constraintIndex++) {
            appendConstraintJson(output, schema.constraints().get(constraintIndex));
            output.append(constraintIndex + 1 < schema.constraints().size() ? ",\n" : "\n");
        }
        output.append("      ]\n")
                .append("    }");
    }

    private static void appendConstraintJson(StringBuilder output, SchemaConstraint constraint) {
        output.append("        {\n")
                .append("          \"type\": ").append(json(constraint.kind().metadataName())).append(",\n")
                .append("          \"trigger\": ")
                .append(constraint.triggerField().map(SchemaArtifactRenderer::json).orElse("null"))
                .append(",\n")
                .append("          \"fields\": ").append(jsonArray(constraint.fields())).append(",\n")
                .append("          \"diagnostic\": ").append(json(constraint.diagnosticCode().value())).append(",\n")
                .append("          \"description\": ").append(json(constraint.description())).append("\n")
                .append("        }");
    }

    private static void appendFieldJson(StringBuilder output, FieldDescriptor field) {
        output.append("        {\n")
                .append("          \"path\": ").append(json(field.path())).append(",\n")
                .append("          \"type\": ").append(json(field.type().authoringName())).append(",\n")
                .append("          \"required\": ").append(field.required()).append(",\n")
                .append("          \"allowed_values\": ").append(jsonArray(field.allowedValues())).append(",\n")
                .append("          \"default\": ").append(field.defaultValue()
                        .map(SchemaArtifactRenderer::defaultJson)
                        .orElse("null"))
                .append(",\n")
                .append("          \"description\": ").append(json(field.description())).append(",\n")
                .append("          \"example\": ").append(json(field.example())).append(",\n")
                .append("          \"diagnostic\": ").append(json(field.diagnosticCode().value())).append(",\n")
                .append("          \"editor\": {\n")
                .append("            \"widget\": ").append(json(field.editor().widget().metadataName())).append(",\n")
                .append("            \"group\": ").append(json(field.editor().group())).append(",\n")
                .append("            \"order\": ").append(field.editor().order()).append(",\n")
                .append("            \"help\": ").append(json(field.editor().help())).append("\n")
                .append("          },\n")
                .append("          \"projection\": ").append(json(field.projection().metadataName())).append(",\n")
                .append("          \"diff\": ").append(json(field.diffPolicy().metadataName())).append(",\n")
                .append("          \"omit_when_default\": ").append(field.omitWhenDefault()).append("\n")
                .append("        }");
    }

    private static void appendDiagnosticJson(StringBuilder output, DiagnosticDescriptor diagnostic) {
        output.append("    {\n")
                .append("      \"code\": ").append(json(diagnostic.code().value())).append(",\n")
                .append("      \"severity\": ")
                .append(json(diagnostic.defaultSeverity().name().toLowerCase(Locale.ROOT))).append(",\n")
                .append("      \"title\": ").append(json(diagnostic.title())).append(",\n")
                .append("      \"why\": ").append(json(diagnostic.whyItMatters())).append(",\n")
                .append("      \"fix\": ").append(json(diagnostic.suggestedFix())).append(",\n")
                .append("      \"docs\": ").append(json(diagnostic.documentationPath())).append(",\n")
                .append("      \"suppressible\": ").append(diagnostic.suppressible()).append("\n")
                .append("    }");
    }

    private static String jsonArray(java.util.List<String> values) {
        return values.stream().map(SchemaArtifactRenderer::json).collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private static int schemaVersion(SchemaRegistry registry) {
        var versions = registry.schemas().stream().map(SchemaDescriptor::currentVersion).distinct().toList();
        if (versions.size() != 1) {
            throw new IllegalArgumentException("Generated catalog requires one current schema version: " + versions);
        }
        return versions.getFirst();
    }

    private static String defaultJson(SchemaDefaultValue value) {
        return switch (value) {
            case SchemaDefaultValue.BooleanValue booleanValue -> Boolean.toString(booleanValue.value());
            case SchemaDefaultValue.IntegerValue integerValue -> Long.toString(integerValue.value());
            case SchemaDefaultValue.DecimalValue decimalValue -> decimalValue.value().toPlainString();
            case SchemaDefaultValue.StringValue stringValue -> json(stringValue.value());
            case SchemaDefaultValue.ListValue listValue -> listValue.values().stream()
                    .map(SchemaArtifactRenderer::defaultJson)
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
            case SchemaDefaultValue.ObjectValue objectValue -> objectValue.fields().entrySet().stream()
                    .map(entry -> json(entry.getKey()) + ": " + defaultJson(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(", ", "{", "}"));
            case SchemaDefaultValue.FieldReference reference -> "{\"$field\": " + json(reference.path()) + "}";
        };
    }

    private static String markdownValue(String value) {
        if ("—".equals(value)) {
            return value;
        }
        return "`" + markdown(value).replace("`", "\\`") + "`";
    }

    private static String markdown(String value) {
        return value.replace("|", "\\|").replace("\n", "<br>");
    }

    private static String json(String value) {
        var output = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        return output.append('"').toString();
    }
}
