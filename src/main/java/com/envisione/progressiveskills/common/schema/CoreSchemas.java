package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionStatus;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;

/** The schema-v2 metadata source used by every generated artifact. */
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
        builder.register(packManifest());
        builder.register(definitionLayer());
        builder.register(definitionPatch());
        builder.register(transactionPlan());
        builder.register(transitionAction());
        builder.register(entitlementContribution());
        builder.register(auditRecord());
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

    private static SchemaDescriptor packManifest() {
        return schema(
                "pack_manifest",
                SchemaAudience.AUTHORING,
                "Content-pack manifest",
                "Identity, compatibility, dependencies, precedence, and fail-closed policy for one content pack.",
                List.of(
                        field("dependencies.incompatible_mods", SchemaValueType.LIST, false,
                                "Loaded mod ids that make this pack invalid.", "[\"incompatible_mod\"]",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.LIST, 150)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("dependencies.optional_mods", SchemaValueType.LIST, false,
                                "Optional mod ids used only by explicitly guarded branches.", "[\"curios\"]",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.LIST, 140)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("dependencies.optional_packs", SchemaValueType.LIST, false,
                                "Optional pack ids with optional @version constraints.", "[\"mypack:magic@>=1.0.0\"]",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.LIST, 120)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("dependencies.required_mods", SchemaValueType.LIST, false,
                                "Mod ids that must be loaded for this pack.", "[\"examplemod\"]",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.LIST, 130)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("dependencies.required_packs", SchemaValueType.LIST, false,
                                "Pack ids that must load first and satisfy optional @version constraints.",
                                "[\"progressiveskills:base@>=1.0.0\"]",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.LIST, 110)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("pack.authors", SchemaValueType.LIST, false,
                                "Bounded author display names.", "[\"Pack Team\"]",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.LIST, 60)
                                .defaultEmptyList().diff(DiffPolicy.ORDERED).omitWhenDefault().build(),
                        field("pack.content_version", SchemaValueType.STRING, true,
                                "Strict SemVer content version used by pack dependencies.", "3.2.0",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 40).build(),
                        field("pack.changelog_url", SchemaValueType.STRING, false,
                                "Optional bounded changelog location retained as pack metadata.",
                                "https://example.invalid/mypack/changelog",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 135).build(),
                        field("pack.default_locale", SchemaValueType.STRING, false,
                                "Lowercase language_country fallback locale.", "en_us",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 90)
                                .defaultString("en_us").omitWhenDefault().build(),
                        field("pack.default_layout", SchemaValueType.RESOURCE_LOCATION, false,
                                "Optional default layout definition used by later presentation phases.",
                                "mypack:character_default",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.RESOURCE_LOCATION, 105).build(),
                        field("pack.default_theme", SchemaValueType.RESOURCE_LOCATION, false,
                                "Optional default theme definition used by later presentation phases.",
                                "mypack:dark_rpg",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.RESOURCE_LOCATION, 100).build(),
                        field("pack.description", SchemaValueType.STRING, false,
                                "Optional bounded pack description retained as metadata.",
                                "An example progression pack.",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.MULTI_LINE, 125).build(),
                        field("pack.engine", SchemaValueType.STRING, true,
                                "Bounded engine SemVer range.", ">=1.0.0 <2.0.0",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 50).build(),
                        field("pack.exported_asset_pack_id", SchemaValueType.RESOURCE_LOCATION, false,
                                "Optional identity of a separately deployed client asset pack.",
                                "mypack:client_assets",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.RESOURCE_LOCATION, 150).build(),
                        field("pack.feature_flags", SchemaValueType.LIST, false,
                                "Declared feature labels retained for compatibility diagnostics.",
                                "[\"core_progression\"]",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.LIST, 140)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("pack.homepage", SchemaValueType.STRING, false,
                                "Optional bounded project homepage retained as pack metadata.",
                                "https://example.invalid/mypack",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 115).build(),
                        field("pack.id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Stable content-pack identity whose namespace owns definitions.", "mypack:core",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("pack.license", SchemaValueType.STRING, true,
                                "Pack redistribution license label.", "All-Rights-Reserved",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 70).build(),
                        field("pack.name", SchemaValueType.COMPONENT, true,
                                "Localized pack display name with required fallback.",
                                "{ key = \"pack.mypack.core\", fallback = \"My Pack\" }",
                                CoreDiagnostics.INVALID_COMPONENT, EditorWidget.COMPONENT, 30).build(),
                        field("pack.namespace", SchemaValueType.STRING, true,
                                "Definition namespace; must equal the pack id namespace.", "mypack",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 20).build(),
                        field("pack.priority", SchemaValueType.INTEGER, false,
                                "Precedence within one root tier; larger values apply later.", "100",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.INTEGER, 80)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("pack.source", SchemaValueType.STRING, false,
                                "Optional bounded source repository location retained as pack metadata.",
                                "https://example.invalid/mypack/source",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SINGLE_LINE, 120).build(),
                        field("pack.trusted_scripts", SchemaValueType.BOOLEAN, false,
                                "Declares that the pack contains trusted-script content; it never grants trust by itself.",
                                "false",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.CHECKBOX, 160)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("policies.duplicate_id", SchemaValueType.ENUM, false,
                                "Duplicate definition policy; Core fails closed.", "error",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.SELECT, 230)
                                .allowedValues("error").defaultString("error").omitWhenDefault().build(),
                        field("policies.merge_conflict", SchemaValueType.ENUM, false,
                                "Ambiguous merge policy; Core fails closed.", "error",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.SELECT, 240)
                                .allowedValues("error").defaultString("error").omitWhenDefault().build(),
                        field("policies.missing_optional", SchemaValueType.ENUM, false,
                                "Behavior for declared optional branches.", "skip_declared_branch",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.SELECT, 210)
                                .allowedValues("skip_declared_branch")
                                .defaultString("skip_declared_branch").omitWhenDefault().build(),
                        field("policies.missing_required", SchemaValueType.ENUM, false,
                                "Behavior for missing required dependencies; Core rejects the pack.", "reject_pack",
                                CoreDiagnostics.MISSING_PACK_DEPENDENCY, EditorWidget.SELECT, 200)
                                .allowedValues("reject_pack").defaultString("reject_pack").omitWhenDefault().build(),
                        field("policies.secret_projection", SchemaValueType.ENUM, false,
                                "Server-only field handling for future client projections.", "redact",
                                CoreDiagnostics.INVALID_PACK_MANIFEST, EditorWidget.SELECT, 250)
                                .allowedValues("redact").defaultString("redact").omitWhenDefault().build(),
                        field("policies.unknown_field", SchemaValueType.ENUM, false,
                                "Unknown manifest/definition field handling.", "error",
                                CoreDiagnostics.UNKNOWN_FIELD, EditorWidget.SELECT, 220)
                                .allowedValues("error").defaultString("error").omitWhenDefault().build(),
                        field("schema_version", SchemaValueType.INTEGER, true,
                                "Manifest authoring schema version.", Integer.toString(CURRENT_SCHEMA_VERSION),
                                CoreDiagnostics.INVALID_SCHEMA_VERSION, EditorWidget.INTEGER, 0)
                                .allowedValues(Integer.toString(CURRENT_SCHEMA_VERSION)).build()
                )
        );
    }

    private static SchemaDescriptor definitionLayer() {
        return schema(
                "definition_layer",
                SchemaAudience.AUTHORING,
                "Definition layer metadata",
                "Reserved top-level metadata controlling deterministic definition collisions.",
                List.of(
                        field("expected_old_digest", SchemaValueType.STRING, false,
                                "Optional lowercase SHA-256 precondition for replace.",
                                "7a9f3d3d2d7d30fc4ff59ef1dbacb9bcf1f0f198f7e0c5d618d66c5c8b112233",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.SINGLE_LINE, 30).build(),
                        field("merge_intent", SchemaValueType.ENUM, false,
                                "Explicit collision behavior for this source file.", "add",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.SELECT, 20)
                                .allowedValues("add", "replace", "merge", "patch", "disable")
                                .defaultString("add").omitWhenDefault().build(),
                        field("patches", SchemaValueType.LIST, false,
                                "Ordered explicit patches; valid only for patch intent.",
                                "[{ op = \"set\", path = \"fallback\", value = \"Updated\" }]",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.LIST, 40)
                                .defaultEmptyList().diff(DiffPolicy.ORDERED).omitWhenDefault().build(),
                        field("schema_version", SchemaValueType.INTEGER, true,
                                "Definition authoring schema version.", Integer.toString(CURRENT_SCHEMA_VERSION),
                                CoreDiagnostics.INVALID_SCHEMA_VERSION, EditorWidget.INTEGER, 10)
                                .allowedValues(Integer.toString(CURRENT_SCHEMA_VERSION)).build()
                ),
                List.of(SchemaConstraint.requires(
                        "expected_old_digest", CoreDiagnostics.MERGE_CONFLICT,
                        "An old-digest precondition is meaningful only with replace intent.", "merge_intent"
                ))
        );
    }

    private static SchemaDescriptor definitionPatch() {
        return schema(
                "definition_patch",
                SchemaAudience.AUTHORING,
                "Definition patch operation",
                "One bounded path-addressed mutation applied before typed schema validation.",
                List.of(
                        field("op", SchemaValueType.ENUM, true,
                                "Patch operation.", "set",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.SELECT, 10)
                                .allowedValues("set", "remove", "append", "prepend", "replace_by_id").build(),
                        field("path", SchemaValueType.STRING, true,
                                "Dotted schema field path.", "style.color",
                                CoreDiagnostics.MERGE_CONFLICT, EditorWidget.SINGLE_LINE, 20).build(),
                        field("target_id", SchemaValueType.RESOURCE_LOCATION, false,
                                "Stable nested id selected by replace_by_id.", "mypack:tree/node",
                                CoreDiagnostics.INVALID_ID, EditorWidget.RESOURCE_LOCATION, 40).build(),
                        field("value", SchemaValueType.ANY, false,
                                "Typed replacement or list value; forbidden for remove.", "red",
                                CoreDiagnostics.INVALID_CANONICAL_VALUE, EditorWidget.OBJECT, 30).build()
                )
        );
    }

    private static SchemaDescriptor transactionPlan() {
        return schema(
                "transaction_plan",
                SchemaAudience.INTERNAL,
                "Progression transaction plan",
                "Bounded, revision- and definition-pinned root plan validated before any mutation.",
                List.of(
                        runtimeField("actor_id", SchemaValueType.STRING,
                                "Authoritative actor UUID.", "00000000-0000-0000-0000-000000000001",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 10).build(),
                        runtimeField("cause", SchemaValueType.ENUM,
                                "Typed progression origin.", "gameplay",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 60)
                                .allowedValues(enumNames(ProgressionCause.values())).build(),
                        runtimeField("definition_generation", SchemaValueType.INTEGER,
                                "Pinned live definition generation.", "7",
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 40).build(),
                        runtimeField("expected_state_revision", SchemaValueType.INTEGER,
                                "Compare-and-swap target revision.", "12",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 30).build(),
                        runtimeField("idempotency_key", SchemaValueType.STRING,
                                "Bounded stable request identity.", "packet/session-1/request-42",
                                CoreDiagnostics.TRANSACTION_LEDGER_FULL, 20).build(),
                        runtimeField("queued_children", SchemaValueType.LIST,
                                "Fully expanded bounded child steps in deterministic order.", "[]",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 90).build(),
                        runtimeField("reason", SchemaValueType.STRING,
                                "Bounded audit reason.", "Award gameplay XP",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 70).build(),
                        runtimeField("root_step", SchemaValueType.OBJECT,
                                "Root balance, ownership, and transition mutations.", "{ origin = \"mypack:rule\" }",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 80).build(),
                        runtimeField("semantic_digest", SchemaValueType.STRING,
                                "Pinned lowercase SHA-256 definition digest.", "a".repeat(64),
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 50).build(),
                        runtimeField("target_id", SchemaValueType.STRING,
                                "Authoritative target UUID.", "00000000-0000-0000-0000-000000000002",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 15).build()
                )
        );
    }

    private static SchemaDescriptor transitionAction() {
        return schema(
                "transition_action",
                SchemaAudience.INTERNAL,
                "Transition action",
                "Typed edge-only action with explicit repeat, delivery, and failure contracts.",
                List.of(
                        runtimeField("amount", SchemaValueType.INTEGER,
                                "Positive bounded action quantity.", "1",
                                CoreDiagnostics.TRANSITION_ACTION_REJECTED, 40).build(),
                        runtimeField("delivery_contract", SchemaValueType.ENUM,
                                "Honest external delivery guarantee.", "effectively_once",
                                CoreDiagnostics.TRANSITION_ACTION_REJECTED, 60)
                                .allowedValues(enumNames(DeliveryContract.values())).build(),
                        runtimeField("failure_policy", SchemaValueType.ENUM,
                                "Whether later actions continue after post-commit failure.", "stop",
                                CoreDiagnostics.TRANSITION_ACTION_REJECTED, 70)
                                .allowedValues(enumNames(TransitionFailurePolicy.values())).build(),
                        runtimeField("payload", SchemaValueType.STRING,
                                "Bounded adapter-specific typed payload.", "minecraft:gold_ingot",
                                CoreDiagnostics.TRANSITION_ACTION_REJECTED, 30).build(),
                        runtimeField("repeat_policy", SchemaValueType.ENUM,
                                "Exact receipt scope or explicit always-repeat behavior.", "once_per_character",
                                CoreDiagnostics.TRANSACTION_LEDGER_FULL, 50)
                                .allowedValues(enumNames(RepeatPolicy.values())).build(),
                        runtimeField("source", SchemaValueType.OBJECT,
                                "Typed owner, definition, and nested grant identity.",
                                "{ owner_kind = \"progressiveskills:skill\", owner_id = \"mypack:physique\" }",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 20).build(),
                        runtimeField("type", SchemaValueType.RESOURCE_LOCATION,
                                "Registered physical action adapter type.", "progressiveskills:item",
                                CoreDiagnostics.TRANSITION_ACTION_REJECTED, 10).build()
                )
        );
    }

    private static SchemaDescriptor entitlementContribution() {
        return schema(
                "entitlement_contribution",
                SchemaAudience.INTERNAL,
                "Persistent entitlement contribution",
                "One source-owned long value resolved with every co-owner before physical projection.",
                List.of(
                        runtimeField("key", SchemaValueType.OBJECT,
                                "Typed persistent target.",
                                "{ target_type = \"progressiveskills:attribute\", target_id = \"minecraft:generic.max_health\" }",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 10).build(),
                        runtimeField("resolver", SchemaValueType.ENUM,
                                "Shared deterministic co-owner resolver.", "highest",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 40)
                                .allowedValues(enumNames(EntitlementResolver.values())).build(),
                        runtimeField("source", SchemaValueType.OBJECT,
                                "Typed grant source whose revocation removes only its contribution.",
                                "{ owner_kind = \"progressiveskills:class\", owner_id = \"mypack:warrior\" }",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 20).build(),
                        runtimeField("value", SchemaValueType.INTEGER,
                                "Checked contribution value.", "4",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 30).build()
                )
        );
    }

    private static SchemaDescriptor auditRecord() {
        return schema(
                "audit_record",
                SchemaAudience.INTERNAL,
                "Transaction audit record",
                "Bounded terminal mutation evidence retaining provenance, revisions, outputs, and rollback classification.",
                List.of(
                        runtimeField("action_results", SchemaValueType.LIST,
                                "Ordered transition dispositions and delivery details.", "[]",
                                CoreDiagnostics.TRANSITION_ACTION_REJECTED, 90).build(),
                        runtimeField("after_revision", SchemaValueType.INTEGER,
                                "Monotonic committed revision or unchanged rejection revision.", "13",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 60).build(),
                        runtimeField("before_revision", SchemaValueType.INTEGER,
                                "Captured target revision.", "12",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 50).build(),
                        runtimeField("completed_at", SchemaValueType.STRING,
                                "Authoritative server completion instant.", "2026-07-16T12:00:00Z",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 40).build(),
                        runtimeField("definition_revision", SchemaValueType.OBJECT,
                                "Generation and semantic digest used by the plan.", "{ generation = 7 }",
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 30).build(),
                        runtimeField("projection_changes", SchemaValueType.LIST,
                                "Source-resolved persistent diff.", "[]",
                                CoreDiagnostics.PERSISTENT_PROJECTION_FAILED, 80).build(),
                        runtimeField("reversible", SchemaValueType.BOOLEAN,
                                "Whether this retained action-free boundary can still be rolled back.", "false",
                                CoreDiagnostics.TRANSACTION_ROLLBACK_REJECTED, 70).build(),
                        runtimeField("status", SchemaValueType.ENUM,
                                "Terminal transaction state.", "committed",
                                CoreDiagnostics.INVALID_LIFECYCLE_OWNERSHIP, 20)
                                .allowedValues(enumNames(TransactionStatus.values())).build(),
                        runtimeField("transaction_id", SchemaValueType.STRING,
                                "Stable transaction UUID derived from the target and idempotency key.",
                                "00000000-0000-0000-0000-000000000004",
                                CoreDiagnostics.TRANSACTION_LEDGER_FULL, 10).build()
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

    private static FieldDescriptor.Builder runtimeField(
            String path,
            SchemaValueType type,
            String description,
            String example,
            DiagnosticCode diagnostic,
            int order
    ) {
        return field(path, type, true, description, example, diagnostic, EditorWidget.OBJECT, order)
                .projection(ProjectionPolicy.SERVER_ONLY);
    }

    private static String[] enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                .toArray(String[]::new);
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
