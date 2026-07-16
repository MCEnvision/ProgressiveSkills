package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.presentation.IconKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;

/** The Phase 2 schema metadata source used by every generated artifact. */
public final class CoreSchemas {
    public static final int CURRENT_SCHEMA_VERSION = SchemaVersion.CURRENT.value();
    private static final String NAMESPACE = "progressiveskills";

    private CoreSchemas() {
    }

    public static SchemaRegistry createRegistry() {
        var builder = SchemaRegistry.builder(CoreDiagnostics.catalog());
        DefinitionKinds.all().forEach(builder::registerKind);
        builder.register(definitionHeader());
        builder.register(alias());
        builder.register(componentSpec());
        builder.register(styleSpec());
        builder.register(iconSpec());
        builder.register(sourceSpan());
        builder.register(canonicalDefinition());
        return builder.build();
    }

    private static SchemaDescriptor definitionHeader() {
        return schema(
                "definition_header",
                SchemaAudience.AUTHORING,
                "Definition header",
                "Versioned identity and presentation fields shared by every definition.",
                List.of(
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized long-form description.",
                                "{ key = \"skill.mypack.physique.desc\", fallback = \"Raw power.\" }",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.COMPONENT, 40).build(),
                        field("display", SchemaValueType.COMPONENT, false,
                                "Localized player-facing display name.",
                                "{ key = \"skill.mypack.physique\", fallback = \"Physique\" }",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.COMPONENT, 30).build(),
                        field("icon", SchemaValueType.ICON, false,
                                "Typed icon descriptor with required fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:iron_chestplate\", fallback = \"minecraft:barrier\", alt = \"Iron chestplate\" }",
                                CoreDiagnostics.MISSING_ALT_TEXT, EditorWidget.ICON, 50).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, false,
                                "Stable typed identity; when written explicitly it must match the source-derived id.",
                                "mypack:combat/physique",
                                CoreDiagnostics.EXPLICIT_ID_MISMATCH, EditorWidget.RESOURCE_LOCATION, 20).build(),
                        field("schema_version", SchemaValueType.INTEGER, true,
                                "Authoring schema version compiled into the canonical IR.",
                                Integer.toString(CURRENT_SCHEMA_VERSION),
                                CoreDiagnostics.INVALID_SCHEMA_VERSION, EditorWidget.INTEGER, 10)
                                .allowedValues(Integer.toString(CURRENT_SCHEMA_VERSION))
                                .build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded, normalized alternate terms used by presentation search.",
                                "[\"Strength\", \"Might\"]",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.LIST, 60)
                                .defaultEmptyList()
                                .diff(DiffPolicy.SET)
                                .omitWhenDefault()
                                .build()
                ),
                List.of(
                        SchemaConstraint.requiredTogether(
                                CoreDiagnostics.INVALID_COMPONENT,
                                "Display and icon are either both present or both omitted.",
                                "display", "icon"
                        ),
                        SchemaConstraint.requires(
                                "description",
                                CoreDiagnostics.INVALID_COMPONENT,
                                "A description is presentation metadata and requires display plus icon.",
                                "display", "icon"
                        ),
                        SchemaConstraint.requires(
                                "search_aliases",
                                CoreDiagnostics.INVALID_COMPONENT,
                                "Non-default search aliases require display plus icon.",
                                "display", "icon"
                        )
                )
        );
    }

    private static SchemaDescriptor alias() {
        return schema(
                "alias",
                SchemaAudience.AUTHORING,
                "Definition alias",
                "A same-kind old identity mapped to one canonical replacement identity.",
                List.of(
                        field("kind", SchemaValueType.RESOURCE_LOCATION, true,
                                "Definition kind shared by the old and replacement identities.",
                                "progressiveskills:skill",
                                CoreDiagnostics.INVALID_ALIAS, EditorWidget.RESOURCE_LOCATION, 10)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("new_id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Canonical replacement identity.",
                                "mypack:combat/physique",
                                CoreDiagnostics.INVALID_ALIAS, EditorWidget.RESOURCE_LOCATION, 30)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("old_id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Retired identity retained for migration and reference resolution.",
                                "mypack:combat/strength",
                                CoreDiagnostics.INVALID_ALIAS, EditorWidget.RESOURCE_LOCATION, 20)
                                .projection(ProjectionPolicy.SERVER_ONLY).build()
                )
        );
    }

    private static SchemaDescriptor componentSpec() {
        return schema(
                "component_spec",
                SchemaAudience.AUTHORING,
                "Component specification",
                "Safe localized text descriptor stored in IR instead of a mutable vanilla Component.",
                List.of(
                        field("children", SchemaValueType.LIST, false,
                                "Bounded child component specifications rendered in order.",
                                "[{ fallback = \"!\", style = { color = \"gold\" } }]",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.LIST, 50)
                                .defaultEmptyList()
                                .diff(DiffPolicy.ORDERED)
                                .omitWhenDefault()
                                .build(),
                        field("fallback", SchemaValueType.STRING, true,
                                "Bounded text used when a locale key cannot be resolved.",
                                "Physique",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.MULTI_LINE, 20).build(),
                        field("key", SchemaValueType.STRING, false,
                                "Pack locale key; omission creates a literal safe component.",
                                "skill.mypack.physique",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.SINGLE_LINE, 10).build(),
                        field("placeholders", SchemaValueType.MAP, false,
                                "Stable placeholder names mapped to declared value types.",
                                "{ level = \"integer\", skill = \"component\" }",
                                CoreDiagnostics.INVALID_PLACEHOLDER, EditorWidget.KEY_VALUE, 30)
                                .defaultEmptyObject()
                                .diff(DiffPolicy.MERGE_BY_KEY)
                                .omitWhenDefault()
                                .build(),
                        field("style", SchemaValueType.OBJECT, false,
                                "Allowlisted, non-interpreting text style.",
                                "{ color = \"red\", bold = true }",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.OBJECT, 40)
                                .defaultEmptyObject()
                                .omitWhenDefault()
                                .build()
                )
        );
    }

    private static SchemaDescriptor styleSpec() {
        return schema(
                "style_spec",
                SchemaAudience.AUTHORING,
                "Safe style specification",
                "Allowlisted visual styling with no click actions, selectors, NBT, or URLs.",
                List.of(
                        field("bold", SchemaValueType.BOOLEAN, false,
                                "Render text with bold emphasis.", "true",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.CHECKBOX, 20)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("color", SchemaValueType.STRING, false,
                                "Named vanilla color or six-digit RGB color.", "red",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.SINGLE_LINE, 10).build(),
                        field("font", SchemaValueType.RESOURCE_LOCATION, false,
                                "Optional namespaced font reference.", "minecraft:default",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.RESOURCE_LOCATION, 70).build(),
                        field("italic", SchemaValueType.BOOLEAN, false,
                                "Render text with italic emphasis.", "false",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.CHECKBOX, 30)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("obfuscated", SchemaValueType.BOOLEAN, false,
                                "Render text with vanilla obfuscation.", "false",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.CHECKBOX, 60)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("strikethrough", SchemaValueType.BOOLEAN, false,
                                "Render text with a strike line.", "false",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.CHECKBOX, 50)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("underlined", SchemaValueType.BOOLEAN, false,
                                "Render text with an underline.", "false",
                                CoreDiagnostics.UNSAFE_PRESENTATION, EditorWidget.CHECKBOX, 40)
                                .defaultBoolean(false).omitWhenDefault().build()
                )
        );
    }

    private static SchemaDescriptor iconSpec() {
        return schema(
                "icon_spec",
                SchemaAudience.AUTHORING,
                "Icon specification",
                "Registry-neutral visual descriptor resolved only at a later presentation boundary.",
                List.of(
                        field("alt", SchemaValueType.COMPONENT, true,
                                "Required narration and nonvisual equivalent.",
                                "Iron chestplate",
                                CoreDiagnostics.MISSING_ALT_TEXT, EditorWidget.COMPONENT, 40).build(),
                        field("fallback", SchemaValueType.RESOURCE_LOCATION, true,
                                "Safe fallback reference used when the preferred icon cannot resolve.",
                                "minecraft:barrier",
                                CoreDiagnostics.INVALID_ICON, EditorWidget.RESOURCE_LOCATION, 30).build(),
                        field("entity_preview_opt_in", SchemaValueType.BOOLEAN, false,
                                "Explicitly permits an entity preview descriptor.",
                                "false",
                                CoreDiagnostics.INVALID_ICON, EditorWidget.CHECKBOX, 60)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("narration", SchemaValueType.COMPONENT, false,
                                "Optional narration text; defaults to the alt component.",
                                "Physique skill icon",
                                CoreDiagnostics.MISSING_ALT_TEXT, EditorWidget.COMPONENT, 50)
                                .defaultFieldReference("alt").omitWhenDefault().build(),
                        field("type", SchemaValueType.ENUM, true,
                                "Tagged icon descriptor kind.",
                                "item",
                                CoreDiagnostics.INVALID_ICON, EditorWidget.SELECT, 10)
                                .allowedValues(Arrays.stream(IconKind.values())
                                        .map(IconKind::serializedName)
                                        .toArray(String[]::new))
                                .build(),
                        field("value", SchemaValueType.RESOURCE_LOCATION, false,
                                "Single namespaced registry, texture, tag, or typed pack-spec identity.",
                                "minecraft:iron_chestplate",
                                CoreDiagnostics.INVALID_ICON, EditorWidget.RESOURCE_LOCATION, 20).build(),
                        field("values", SchemaValueType.LIST, false,
                                "Ordered layer references used only by composite badges.",
                                "[\"mypack:base\", \"mypack:badge\"]",
                                CoreDiagnostics.INVALID_ICON, EditorWidget.LIST, 25)
                                .diff(DiffPolicy.ORDERED)
                                .build()
                ),
                List.of(
                        SchemaConstraint.exactlyOne(
                                CoreDiagnostics.INVALID_ICON,
                                "Use value for a single/tag identity or values for composite badge layers.",
                                "value", "values"
                        )
                )
        );
    }

    private static SchemaDescriptor sourceSpan() {
        return schema(
                "source_span",
                SchemaAudience.INTERNAL,
                "Source span",
                "Normalized field provenance kept outside semantic equality and future digests.",
                List.of(
                        internalField("end", SchemaValueType.OBJECT,
                                "Exclusive one-based ending position.", "{ line = 4, column = 8 }", 30),
                        internalField("source", SchemaValueType.STRING,
                                "POSIX relative source identifier without a host path.", "skills/combat/physique.toml", 10),
                        internalField("start", SchemaValueType.OBJECT,
                                "Inclusive one-based starting position.", "{ line = 4, column = 1 }", 20)
                )
        );
    }

    private static SchemaDescriptor canonicalDefinition() {
        return schema(
                "canonical_definition",
                SchemaAudience.INTERNAL,
                "Canonical definition envelope",
                "Immutable typed semantic fields paired with source maps and provenance.",
                List.of(
                        internalField("fields", SchemaValueType.MAP,
                                "Deterministically ordered typed canonical values.", "{ max_level = 100 }", 20),
                        internalField("header", SchemaValueType.OBJECT,
                                "Schema version, typed stable key, and safe presentation.", "{ schema_version = 2, id = \"mypack:physique\" }", 10),
                        field("provenance", SchemaValueType.OBJECT, true,
                                "Adapter and source metadata excluded from semantic projection.",
                                "{ adapter = \"toml\" }",
                                CoreDiagnostics.INVALID_SOURCE_SPAN, EditorWidget.OBJECT, 30)
                                .projection(ProjectionPolicy.SERVER_ONLY)
                                .build(),
                        field("source_map", SchemaValueType.MAP, true,
                                "Field path to source-span mapping excluded from semantic projection.",
                                "{ max_level = { source = \"skills/physique.toml\" } }",
                                CoreDiagnostics.INVALID_SOURCE_SPAN, EditorWidget.KEY_VALUE, 40)
                                .projection(ProjectionPolicy.SERVER_ONLY)
                                .diff(DiffPolicy.MERGE_BY_KEY)
                                .build()
                )
        );
    }

    private static FieldDescriptor internalField(
            String path,
            SchemaValueType type,
            String description,
            String example,
            int order
    ) {
        return field(path, type, true, description, example,
                CoreDiagnostics.INVALID_CANONICAL_VALUE, EditorWidget.OBJECT, order)
                .projection(ProjectionPolicy.SERVER_ONLY)
                .build();
    }

    private static SchemaDescriptor schema(
            String path,
            SchemaAudience audience,
            String title,
            String description,
            List<FieldDescriptor> fields
    ) {
        return schema(path, audience, title, description, fields, List.of());
    }

    private static SchemaDescriptor schema(
            String path,
            SchemaAudience audience,
            String title,
            String description,
            List<FieldDescriptor> fields,
            List<SchemaConstraint> constraints
    ) {
        return new SchemaDescriptor(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
                CURRENT_SCHEMA_VERSION,
                audience,
                title,
                description,
                fields,
                constraints
        );
    }

    private static FieldDescriptor.Builder field(
            String path,
            SchemaValueType type,
            boolean required,
            String description,
            String example,
            DiagnosticCode diagnostic,
            EditorWidget widget,
            int order
    ) {
        var builder = FieldDescriptor.builder(path, type)
                .description(description)
                .example(example)
                .diagnostic(diagnostic)
                .projection(ProjectionPolicy.CLIENT_VISIBLE)
                .editor(new EditorHint(widget, "core", order, description));
        if (required) {
            builder.required();
        }
        return builder;
    }
}
