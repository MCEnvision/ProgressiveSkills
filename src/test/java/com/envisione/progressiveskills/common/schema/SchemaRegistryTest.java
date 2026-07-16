package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.presentation.IconKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaRegistryTest {
    @Test
    void coreRegistryIsCompleteAndDeterministicallyOrdered() {
        var registry = CoreSchemas.createRegistry();
        var ids = registry.schemas().stream().map(schema -> schema.id().toString()).toList();

        assertEquals(ids.stream().sorted().toList(), ids);
        assertEquals(19, ids.size());
        assertEquals(DefinitionKinds.all(), registry.definitionKinds().stream().toList());
        for (var schema : registry.schemas()) {
            assertEquals(
                    schema.fields().stream().sorted().toList(),
                    schema.fields(),
                    () -> schema.id() + " fields must have a stable order"
            );
            for (var field : schema.fields()) {
                registry.diagnostics().require(field.diagnosticCode());
            }
        }
    }

    @Test
    void coreMetadataDerivesFrozenVocabulariesAndKeepsSharedPresentationOptional() {
        var registry = CoreSchemas.createRegistry();
        var header = registry.require(ResourceLocation.fromNamespaceAndPath("progressiveskills", "definition_header"));
        var fields = header.fields().stream()
                .collect(Collectors.toMap(FieldDescriptor::path, Function.identity()));
        var icon = registry.require(ResourceLocation.fromNamespaceAndPath("progressiveskills", "icon_spec"));
        var iconType = icon.fields().stream()
                .filter(field -> field.path().equals("type"))
                .findFirst()
                .orElseThrow();
        var canonical = registry.require(ResourceLocation.fromNamespaceAndPath(
                "progressiveskills", "canonical_definition"
        ));
        var canonicalFields = canonical.fields().stream()
                .collect(Collectors.toMap(FieldDescriptor::path, Function.identity()));

        assertEquals(SchemaVersion.CURRENT.value(), CoreSchemas.CURRENT_SCHEMA_VERSION);
        assertEquals(
                Arrays.stream(IconKind.values()).map(IconKind::serializedName).toList(),
                iconType.allowedValues()
        );
        assertFalse(fields.get("id").required());
        assertFalse(fields.get("display").required());
        assertFalse(fields.get("icon").required());
        assertEquals(DiffPolicy.SET, fields.get("search_aliases").diffPolicy());
        assertEquals(new SchemaDefaultValue.ListValue(List.of()), fields.get("search_aliases").defaultValue().orElseThrow());
        assertEquals(3, header.constraints().size());
        assertEquals(
                List.of(SchemaConstraintKind.REQUIRED_TOGETHER, SchemaConstraintKind.REQUIRES, SchemaConstraintKind.REQUIRES),
                header.constraints().stream().map(SchemaConstraint::kind).toList()
        );
        assertEquals(
                List.of(SchemaConstraintKind.EXACTLY_ONE),
                icon.constraints().stream().map(SchemaConstraint::kind).toList()
        );
        assertTrue(canonicalFields.get("provenance").required());
        assertTrue(canonicalFields.get("source_map").required());
        var manifest = registry.require(ResourceLocation.fromNamespaceAndPath("progressiveskills", "pack_manifest"));
        assertTrue(manifest.fields().stream().anyMatch(field -> field.path().equals("pack.engine")));
        var manifestPaths = manifest.fields().stream().map(FieldDescriptor::path).collect(Collectors.toSet());
        assertTrue(manifestPaths.containsAll(List.of(
                "pack.default_theme", "pack.default_layout", "pack.homepage", "pack.source",
                "pack.description", "pack.changelog_url", "pack.feature_flags",
                "pack.exported_asset_pack_id", "pack.trusted_scripts"
        )));
        assertEquals(
                List.of("error"),
                manifest.fields().stream().filter(field -> field.path().equals("policies.unknown_field"))
                        .findFirst().orElseThrow().allowedValues()
        );
        var layer = registry.require(ResourceLocation.fromNamespaceAndPath("progressiveskills", "definition_layer"));
        assertEquals(
                List.of("add", "replace", "merge", "patch", "disable"),
                layer.fields().stream().filter(field -> field.path().equals("merge_intent"))
                        .findFirst().orElseThrow().allowedValues()
        );
        var transaction = registry.require(ResourceLocation.fromNamespaceAndPath("progressiveskills", "transaction_plan"));
        assertTrue(transaction.fields().stream().anyMatch(field -> field.path().equals("idempotency_key")));
        var action = registry.require(ResourceLocation.fromNamespaceAndPath("progressiveskills", "transition_action"));
        assertEquals(
                List.of("always", "once_per_transaction", "once_per_character"),
                action.fields().stream().filter(field -> field.path().equals("repeat_policy"))
                        .findFirst().orElseThrow().allowedValues()
        );
    }

    @Test
    void kindRegistrationIsIdempotentButRejectsDirectoryCollisions() {
        var builder = SchemaRegistry.builder(CoreDiagnostics.catalog());
        var first = DefinitionKind.of("addon:reputation", "reputations");
        var equivalent = DefinitionKind.of("addon:reputation", "reputations");
        var conflicting = DefinitionKind.of("addon:reputation", "reputation_defs");
        var directoryCollision = DefinitionKind.of("other:reputation", "reputations");

        builder.registerKind(first).registerKind(equivalent);
        var exception = assertThrows(IllegalArgumentException.class, () -> builder.registerKind(conflicting));
        assertEquals(
                "Definition kind addon:reputation maps to conflicting source directories: reputations and reputation_defs",
                exception.getMessage()
        );
        var directoryException = assertThrows(
                IllegalArgumentException.class,
                () -> builder.registerKind(directoryCollision)
        );
        assertEquals(
                "Definition source directory reputations is shared by addon:reputation and other:reputation",
                directoryException.getMessage()
        );

        var registry = builder.build();
        assertSame(first, registry.requireKind(first.id()));
        assertThrows(UnsupportedOperationException.class, () -> registry.definitionKinds().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.requireKind(ResourceLocation.fromNamespaceAndPath("addon", "unknown"))
        );
        assertThrows(IllegalStateException.class, () -> builder.registerKind(first));
    }

    @Test
    void descriptorsDefensivelyCopyAndRejectDuplicateFields() {
        var fields = new ArrayList<FieldDescriptor>();
        fields.add(fixtureField("first"));
        var schema = fixtureSchema(fields);
        fields.add(fixtureField("later_mutation"));

        assertEquals(List.of("first"), schema.fields().stream().map(FieldDescriptor::path).toList());
        assertThrows(UnsupportedOperationException.class, () -> schema.fields().add(fixtureField("blocked")));
        assertThrows(IllegalArgumentException.class, () -> fixtureSchema(List.of(
                fixtureField("same"),
                fixtureField("same")
        )));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaDescriptor(
                        ResourceLocation.fromNamespaceAndPath("", "fixture"),
                        2,
                        SchemaAudience.INTERNAL,
                        "Invalid schema",
                        "Schema IDs require an explicit namespace.",
                        List.of(fixtureField("field"))
                )
        );
    }

    @Test
    void registryRejectsDuplicateIdsAndCannotBeReopenedAfterBuild() {
        var builder = SchemaRegistry.builder(CoreDiagnostics.catalog());
        var schema = fixtureSchema(List.of(fixtureField("field")));
        builder.register(schema);

        assertThrows(IllegalArgumentException.class, () -> builder.register(schema));
        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.register(schema));
    }

    @Test
    void registryRejectsUnknownDiagnosticsAndSchemaDefaultsFailClosed() {
        var unknown = FieldDescriptor.builder("unknown", SchemaValueType.STRING)
                .description("Unknown diagnostic fixture.")
                .example("fixture")
                .diagnostic(new DiagnosticCode("PS-TEST-999"))
                .editor(new EditorHint(EditorWidget.SINGLE_LINE, "fixture", 0, "Fixture help."))
                .build();

        assertEquals(ProjectionPolicy.SERVER_ONLY, unknown.projection());
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaRegistry.builder(CoreDiagnostics.catalog()).register(fixtureSchema(List.of(unknown)))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FieldDescriptor.builder("bad_default", SchemaValueType.BOOLEAN)
                        .description("Bad default fixture.")
                        .example("false")
                        .diagnostic(CoreDiagnostics.UNKNOWN_FIELD)
                        .editor(new EditorHint(EditorWidget.CHECKBOX, "fixture", 0, "Fixture help."))
                        .defaultString("false")
                        .build()
        );
    }

    @Test
    void fieldReferenceDefaultsMustTargetACompatibleSibling() {
        var source = FieldDescriptor.builder("source", SchemaValueType.COMPONENT)
                .description("Source component.")
                .example("Source")
                .diagnostic(CoreDiagnostics.INVALID_COMPONENT)
                .editor(new EditorHint(EditorWidget.COMPONENT, "fixture", 0, "Source help."))
                .build();
        var inherited = FieldDescriptor.builder("inherited", SchemaValueType.COMPONENT)
                .description("Inherited component.")
                .example("Inherited")
                .diagnostic(CoreDiagnostics.INVALID_COMPONENT)
                .editor(new EditorHint(EditorWidget.COMPONENT, "fixture", 1, "Inherited help."))
                .defaultFieldReference("source")
                .build();

        fixtureSchema(List.of(inherited, source));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureSchema(List.of(
                        FieldDescriptor.builder("missing_reference", SchemaValueType.COMPONENT)
                                .description("Missing reference.")
                                .example("Missing")
                                .diagnostic(CoreDiagnostics.INVALID_COMPONENT)
                                .editor(new EditorHint(EditorWidget.COMPONENT, "fixture", 0, "Missing help."))
                                .defaultFieldReference("absent")
                                .build()
                ))
        );
        var first = FieldDescriptor.builder("first", SchemaValueType.STRING)
                .description("First cycle field.")
                .example("first")
                .diagnostic(CoreDiagnostics.UNKNOWN_FIELD)
                .editor(new EditorHint(EditorWidget.SINGLE_LINE, "fixture", 0, "First help."))
                .defaultFieldReference("second")
                .build();
        var second = FieldDescriptor.builder("second", SchemaValueType.STRING)
                .description("Second cycle field.")
                .example("second")
                .diagnostic(CoreDiagnostics.UNKNOWN_FIELD)
                .editor(new EditorHint(EditorWidget.SINGLE_LINE, "fixture", 1, "Second help."))
                .defaultFieldReference("first")
                .build();
        assertThrows(IllegalArgumentException.class, () -> fixtureSchema(List.of(first, second)));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureSchema(List.of(
                        FieldDescriptor.builder("self", SchemaValueType.COMPONENT)
                                .description("Self reference.")
                                .example("Self")
                                .diagnostic(CoreDiagnostics.INVALID_COMPONENT)
                                .editor(new EditorHint(EditorWidget.COMPONENT, "fixture", 0, "Self help."))
                                .defaultFieldReference("self")
                                .build()
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixtureSchema(List.of(
                        source,
                        FieldDescriptor.builder("wrong_type", SchemaValueType.STRING)
                                .description("Wrong reference type.")
                                .example("Wrong")
                                .diagnostic(CoreDiagnostics.INVALID_COMPONENT)
                                .editor(new EditorHint(EditorWidget.SINGLE_LINE, "fixture", 1, "Wrong help."))
                                .defaultFieldReference("source")
                                .build()
                ))
        );
    }

    private static SchemaDescriptor fixtureSchema(List<FieldDescriptor> fields) {
        return new SchemaDescriptor(
                ResourceLocation.fromNamespaceAndPath("test", "fixture"),
                2,
                SchemaAudience.INTERNAL,
                "Fixture schema",
                "A schema used to exercise immutable registry behavior.",
                fields
        );
    }

    private static FieldDescriptor fixtureField(String path) {
        return FieldDescriptor.builder(path, SchemaValueType.STRING)
                .description("Fixture field description.")
                .example("fixture")
                .diagnostic(CoreDiagnostics.UNKNOWN_FIELD)
                .editor(new EditorHint(EditorWidget.SINGLE_LINE, "fixture", 0, "Fixture help."))
                .build();
    }
}
