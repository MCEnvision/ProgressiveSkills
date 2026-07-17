package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.CurveRounding;
import com.envisione.progressiveskills.common.skill.CurveType;
import com.envisione.progressiveskills.common.rule.FakePlayerPolicy;
import com.envisione.progressiveskills.common.rule.RuleMultiplierMode;
import com.envisione.progressiveskills.common.rule.RuleMultiplierStage;
import com.envisione.progressiveskills.common.rule.RuleStackRule;
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
        builder.register(skillDefinition());
        builder.register(currencyDefinition());
        builder.register(ruleDefinition());
        builder.register(transactionPlan());
        builder.register(transitionAction());
        builder.register(entitlementContribution());
        builder.register(auditRecord());
        builder.register(playerDataAttachment());
        builder.register(storedDefinitionState());
        builder.register(operationReceipt());
        builder.register(pendingProgressionOperation());
        builder.register(playerDataSnapshot());
        builder.register(networkHandshake());
        builder.register(definitionProjection());
        builder.register(transferEnvelope());
        builder.register(visiblePlayerState());
        builder.register(stateDelta());
        builder.register(networkIntent());
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

    private static SchemaDescriptor skillDefinition() {
        return schema(
                "skill_definition",
                SchemaAudience.AUTHORING,
                "Skill definition",
                "Fixed point XP progression with an exact curve, named currency awards, and source owned attributes.",
                List.of(
                        field("curve.base", SchemaValueType.DECIMAL, false,
                                "Base value used by flat, linear, polynomial, and exponential curves.", "100",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.DECIMAL, 110).build(),
                        field("curve.coefficient", SchemaValueType.DECIMAL, false,
                                "Polynomial coefficient multiplied by the level offset power.", "15",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.DECIMAL, 130).build(),
                        field("curve.custom_table", SchemaValueType.LIST, false,
                                "One exact outgoing XP cost for every level below the hard cap.", "[100, 125, 150]",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.LIST, 160)
                                .diff(DiffPolicy.ORDERED).build(),
                        field("curve.factor", SchemaValueType.DECIMAL, false,
                                "Positive exponential multiplier raised to the level offset.", "1.15",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.DECIMAL, 150).build(),
                        field("curve.power", SchemaValueType.INTEGER, false,
                                "Nonnegative bounded integer polynomial power.", "2",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.INTEGER, 140).build(),
                        field("curve.rounding", SchemaValueType.ENUM, false,
                                "One final rounding operation applied after the complete level cost expression.", "ceil",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.SELECT, 170)
                                .allowedValues(Arrays.stream(CurveRounding.values())
                                        .map(CurveRounding::serializedName).toArray(String[]::new))
                                .defaultString("ceil").omitWhenDefault().build(),
                        field("curve.step", SchemaValueType.DECIMAL, false,
                                "Linear amount multiplied by the level offset.", "25",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.DECIMAL, 120).build(),
                        field("curve.type", SchemaValueType.ENUM, true,
                                "Exact Core XP curve family.", "linear",
                                CoreDiagnostics.INVALID_SKILL_CURVE, EditorWidget.SELECT, 100)
                                .allowedValues(Arrays.stream(CurveType.values())
                                        .map(CurveType::serializedName).toArray(String[]::new))
                                .build(),
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized skill description.", "{ fallback = \"Raw physical conditioning.\" }",
                                CoreDiagnostics.INVALID_SKILL, EditorWidget.COMPONENT, 30).build(),
                        field("display", SchemaValueType.COMPONENT, true,
                                "Localized skill name used by feedback and presentation.", "{ fallback = \"Physique\" }",
                                CoreDiagnostics.INVALID_SKILL, EditorWidget.COMPONENT, 20).build(),
                        field("enabled", SchemaValueType.BOOLEAN, false,
                                "Whether the skill accepts XP and projects grants.", "true",
                                CoreDiagnostics.INVALID_SKILL, EditorWidget.CHECKBOX, 50)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("icon", SchemaValueType.ICON, true,
                                "Skill icon with fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:iron_chestplate\", fallback = \"minecraft:barrier\", alt = \"Iron chestplate\" }",
                                CoreDiagnostics.INVALID_SKILL, EditorWidget.ICON, 40).build(),
                        field("level_currency_awards", SchemaValueType.LIST, false,
                                "Named currency entitlements awarded only for newly crossed lifetime highest levels.",
                                "[{ id = \"mypack:physique/points\", currency = \"progressiveskills:global_points\", amount_per_level = 1 }]",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.LIST, 200)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("levels", SchemaValueType.LIST, false,
                                "Discrete source owned attribute effects activated at exact levels.",
                                "[{ id = \"mypack:physique/level_1\", level = 1, effects = [] }]",
                                CoreDiagnostics.INVALID_ATTRIBUTE_GRANT, EditorWidget.LIST, 220)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("max_level", SchemaValueType.INTEGER, true,
                                "Inclusive hard skill level cap.", "10",
                                CoreDiagnostics.INVALID_SKILL, EditorWidget.INTEGER, 70).build(),
                        field("min_level", SchemaValueType.INTEGER, false,
                                "Initial level and curve offset origin.", "0",
                                CoreDiagnostics.INVALID_SKILL, EditorWidget.INTEGER, 60)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("negative_xp_policy", SchemaValueType.ENUM, false,
                                "Phase 7 negative XP behavior.", "deny",
                                CoreDiagnostics.INVALID_XP_AWARD, EditorWidget.SELECT, 90)
                                .allowedValues("deny").defaultString("deny").omitWhenDefault().build(),
                        field("overflow", SchemaValueType.ENUM, false,
                                "Destination for XP earned at the hard cap.", "bank",
                                CoreDiagnostics.INVALID_XP_AWARD, EditorWidget.SELECT, 80)
                                .allowedValues("bank").defaultString("bank").omitWhenDefault().build(),
                        field("scaling", SchemaValueType.LIST, false,
                                "Uniform per level source owned attribute grants over a bounded range.",
                                "[{ id = \"mypack:physique/health\", type = \"attribute\", attribute = \"minecraft:generic.max_health\", operation = \"add_value\", per_level = 2.0 }]",
                                CoreDiagnostics.INVALID_ATTRIBUTE_GRANT, EditorWidget.LIST, 230)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("xp_sources", SchemaValueType.LIST, false,
                                "Stable custom XP routes compiled into authoritative fixed point awards.",
                                "[{ id = \"mypack:physique/training\", action = \"custom\", key = \"mypack:training\", amount = 25 }]",
                                CoreDiagnostics.INVALID_XP_AWARD, EditorWidget.LIST, 210)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build()
                )
        );
    }

    private static SchemaDescriptor currencyDefinition() {
        return schema(
                "currency_definition",
                SchemaAudience.AUTHORING,
                "Named currency definition",
                "Checked character scoped integer balance referenced by progression definitions.",
                List.of(
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized currency description.", "{ fallback = \"Points from skill levels.\" }",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.COMPONENT, 30).build(),
                        field("display", SchemaValueType.COMPONENT, true,
                                "Localized currency name.", "{ fallback = \"Global Points\" }",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.COMPONENT, 20).build(),
                        field("icon", SchemaValueType.ICON, true,
                                "Currency icon with fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:emerald\", fallback = \"minecraft:barrier\", alt = \"Emerald\" }",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.ICON, 40).build(),
                        field("initial", SchemaValueType.INTEGER, false,
                                "Balance installed when the currency is first reconciled.", "0",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.INTEGER, 70)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("maximum", SchemaValueType.INTEGER, false,
                                "Inclusive checked upper balance bound.", "1000000000",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.INTEGER, 60).build(),
                        field("minimum", SchemaValueType.INTEGER, false,
                                "Inclusive checked lower balance bound.", "0",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.INTEGER, 50)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("scope", SchemaValueType.ENUM, false,
                                "Phase 7 authority scope.", "character",
                                CoreDiagnostics.INVALID_CURRENCY, EditorWidget.SELECT, 80)
                                .allowedValues("character").defaultString("character").omitWhenDefault().build()
                )
        );
    }

    private static SchemaDescriptor ruleDefinition() {
        return schema(
                "rule_definition",
                SchemaAudience.AUTHORING,
                "Gameplay rule definition",
                "Compiled trigger route with literal fixed point output and bounded anti exploit memory.",
                List.of(
                        field("allow_custom_name", SchemaValueType.BOOLEAN, false,
                                "Explicit opt in for normalized player controlled custom name matching.", "false",
                                CoreDiagnostics.INVALID_RULE_MATCHER, EditorWidget.CHECKBOX, 80)
                                .defaultBoolean(false).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.cooldown_ticks", SchemaValueType.INTEGER, false,
                                "Minimum world ticks between committed awards from this source.", "10",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.INTEGER, 210)
                                .defaultInteger(0).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.fake_players", SchemaValueType.ENUM, false,
                                "Whether automation identities may receive this route.", "deny",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.SELECT, 190)
                                .allowedValues(Arrays.stream(FakePlayerPolicy.values())
                                        .map(FakePlayerPolicy::serializedName).toArray(String[]::new))
                                .defaultString("deny").omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.first_time", SchemaValueType.BOOLEAN, false,
                                "Persist one receipt like source marker and reject later awards.", "false",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.CHECKBOX, 200)
                                .defaultBoolean(false).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.minimum_multiplier", SchemaValueType.DECIMAL, false,
                                "Floor for repeated source decay.", "0.25",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.DECIMAL, 270)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.per_day_cap", SchemaValueType.DECIMAL, false,
                                "Maximum fixed point XP from this source per Minecraft day bucket.", "400",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.DECIMAL, 240)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.per_minute_cap", SchemaValueType.DECIMAL, false,
                                "Maximum fixed point XP from this source per 1200 tick bucket.", "40",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.DECIMAL, 230)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.per_tick_cap", SchemaValueType.DECIMAL, false,
                                "Maximum fixed point XP from this source in one world tick.", "10",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.DECIMAL, 220)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.repeat_decay", SchemaValueType.DECIMAL, false,
                                "Multiplier applied for each repeated source event inside the repeat window.", "0.5",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.DECIMAL, 260)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("anti_exploit.repeat_window_ticks", SchemaValueType.INTEGER, false,
                                "Bounded source repetition window in world ticks.", "100",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.INTEGER, 250)
                                .defaultInteger(0).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("base", SchemaValueType.DECIMAL, true,
                                "Positive literal fixed point amount before multiplier stacks and caps.", "8",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.DECIMAL, 100)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("credit", SchemaValueType.ENUM, false,
                                "Phase 8 credit subject.", "actor",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.SELECT, 60)
                                .allowedValues("actor").defaultString("actor").omitWhenDefault()
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("enabled", SchemaValueType.BOOLEAN, false,
                                "Whether this rule is present in its compiled trigger table.", "true",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.CHECKBOX, 20)
                                .defaultBoolean(true).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("match", SchemaValueType.LIST, false,
                                "OR matched subjects with bare ids, prefixes, and optional leading negation filters.",
                                "[\"id:minecraft:stone\", \"tag:minecraft:logs\"]",
                                CoreDiagnostics.INVALID_RULE_MATCHER, EditorWidget.LIST, 70)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault()
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("multipliers", SchemaValueType.LIST, false,
                                "Literal modifiers resolved by fixed stage and stable stack group.",
                                "[{ id = \"mypack:training/context\", stage = \"context\", group = \"mypack:training\", mode = \"add\", value = 0.25 }]",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.LIST, 110)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault()
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("multipliers.mode", SchemaValueType.ENUM, false,
                                "Stack resolution within one literal multiplier group.", "add",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.SELECT, 112)
                                .allowedValues(Arrays.stream(RuleMultiplierMode.values())
                                        .map(RuleMultiplierMode::serializedName).toArray(String[]::new))
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("multipliers.stage", SchemaValueType.ENUM, false,
                                "Fixed multiplier pipeline stage.", "context",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.SELECT, 111)
                                .allowedValues(Arrays.stream(RuleMultiplierStage.values())
                                        .map(RuleMultiplierStage::serializedName).toArray(String[]::new))
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("outputs", SchemaValueType.LIST, true,
                                "Exactly one Phase 8 XP output using rule_amount.",
                                "[{ id = \"mypack:stone/xp\", type = \"xp\", skill = \"mypack:mining\", amount_formula = \"rule_amount\" }]",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.LIST, 120)
                                .diff(DiffPolicy.MERGE_BY_KEY).projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("priority", SchemaValueType.INTEGER, false,
                                "Higher priority wins first and exclusive route selection.", "100",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.INTEGER, 40)
                                .defaultInteger(0).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("stack_group", SchemaValueType.RESOURCE_LOCATION, false,
                                "Stable group for overlapping matching rules.", "mypack:ore_mining",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.RESOURCE_LOCATION, 50)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("stack_rule", SchemaValueType.ENUM, false,
                                "Deterministic overlap policy for the stable route group.", "highest",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.SELECT, 55)
                                .allowedValues(Arrays.stream(RuleStackRule.values())
                                        .map(RuleStackRule::serializedName).toArray(String[]::new))
                                .defaultString("sum").omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("trigger", SchemaValueType.RESOURCE_LOCATION, true,
                                "Registered server side event route.", "progressiveskills:block_break",
                                CoreDiagnostics.UNKNOWN_RULE_TRIGGER, EditorWidget.RESOURCE_LOCATION, 30)
                                .projection(ProjectionPolicy.SERVER_ONLY).build()
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

    private static SchemaDescriptor playerDataAttachment() {
        return schema(
                "player_data_attachment",
                SchemaAudience.INTERNAL,
                "Versioned player data attachment",
                "Bounded durable progression state with transaction truth, migration evidence, and fail-closed quarantine.",
                List.of(
                        runtimeField("data_version", SchemaValueType.INTEGER,
                                "Persisted attachment contract version.", "2",
                                CoreDiagnostics.PLAYER_DATA_MIGRATION_FAILED, 10).build(),
                        runtimeOptionalField("death_marker", SchemaValueType.OBJECT,
                                "Two-step death-copy operation marker and completion receipt.", "{ transaction_id = \"00000000-0000-0000-0000-000000000004\" }",
                                CoreDiagnostics.PLAYER_DATA_QUARANTINED, 80).build(),
                        runtimeField("definition_states", SchemaValueType.LIST,
                                "Bounded typed states keyed by stable definition identity and lineage.", "[]",
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 50).build(),
                        runtimeOptionalField("extensions", SchemaValueType.MAP,
                                "Unknown bounded fields retained for forward-compatible round trips.", "{}",
                                CoreDiagnostics.PLAYER_DATA_LIMIT_EXCEEDED, 110).build(),
                        runtimeOptionalField("migration_shadow", SchemaValueType.OBJECT,
                                "Bounded pre-migration raw evidence retained through the first successful save.", "{ source_version = 1 }",
                                CoreDiagnostics.PLAYER_DATA_MIGRATION_FAILED, 90).build(),
                        runtimeField("operation_receipts", SchemaValueType.LIST,
                                "Exact same-attachment receipts for death and offline operation completion.", "[]",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 70).build(),
                        runtimeField("orphans", SchemaValueType.LIST,
                                "Persisted definition state awaiting a compatible definition or explicit replacement.", "[]",
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 60).build(),
                        runtimeField("player_id", SchemaValueType.STRING,
                                "UUID that must match the attachment owner.", "00000000-0000-0000-0000-000000000001",
                                CoreDiagnostics.PLAYER_DATA_IDENTITY_MISMATCH, 20).build(),
                        runtimeOptionalField("quarantine", SchemaValueType.OBJECT,
                                "Fail-closed reason, digest, and bounded raw evidence.", "{ reason = \"future data version\" }",
                                CoreDiagnostics.PLAYER_DATA_QUARANTINED, 100).build(),
                        runtimeOptionalField("state_definition", SchemaValueType.OBJECT,
                                "Definition generation and digest associated with the persisted account.", "{ generation = 7 }",
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 35).build(),
                        runtimeField("status", SchemaValueType.ENUM,
                                "Active or quarantined projection state.", "ACTIVE",
                                CoreDiagnostics.PLAYER_DATA_QUARANTINED, 25)
                                .allowedValues("ACTIVE", "QUARANTINED").build(),
                        runtimeField("storage_revision", SchemaValueType.INTEGER,
                                "Monotonic attachment mutation revision.", "12",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 30).build(),
                        runtimeField("transaction", SchemaValueType.OBJECT,
                                "Exact revision, balances, ownership, receipts, replay results, and audit state.", "{ state_revision = 12 }",
                                CoreDiagnostics.TRANSACTION_LEDGER_FULL, 40).build()
                )
        );
    }

    private static SchemaDescriptor storedDefinitionState() {
        return schema(
                "stored_definition_state",
                SchemaAudience.INTERNAL,
                "Stored definition state",
                "Versioned per-definition payload whose stable lineage supports explicit aliases and compatible restoration.",
                List.of(
                        runtimeField("id", SchemaValueType.RESOURCE_LOCATION,
                                "Stable definition identity.", "mypack:physique",
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 10).build(),
                        runtimeField("kind", SchemaValueType.RESOURCE_LOCATION,
                                "Typed definition-kind identity.", "progressiveskills:skill",
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 15).build(),
                        runtimeField("kind_directory", SchemaValueType.STRING,
                                "Source directory retained for provider-defined kinds.", "skills",
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 17).build(),
                        runtimeField("lineage", SchemaValueType.STRING,
                                "Semantic lineage required for compatibility checks.", "a".repeat(64),
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 20).build(),
                        runtimeField("origin_lineage", SchemaValueType.STRING,
                                "Original lineage retained across an explicit identity replacement.", "b".repeat(64),
                                CoreDiagnostics.PLAYER_STATE_ORPHANED, 30).build(),
                        runtimeField("payload", SchemaValueType.OBJECT,
                                "Bounded definition-specific NBT.", "{ level = 4 }",
                                CoreDiagnostics.PLAYER_DATA_LIMIT_EXCEEDED, 50).build(),
                        runtimeField("payload_version", SchemaValueType.INTEGER,
                                "Definition payload contract version.", "1",
                                CoreDiagnostics.PLAYER_DATA_MIGRATION_FAILED, 40).build()
                )
        );
    }

    private static SchemaDescriptor operationReceipt() {
        return schema(
                "operation_receipt",
                SchemaAudience.INTERNAL,
                "Durable operation receipt",
                "Bounded same-attachment evidence that an offline or death-copy operation completed.",
                List.of(
                        runtimeField("applied_at", SchemaValueType.INTEGER,
                                "Authoritative completion epoch milliseconds.", "1784203200000",
                                CoreDiagnostics.PLAYER_DATA_QUARANTINED, 40).build(),
                        runtimeField("detail", SchemaValueType.STRING,
                                "Bounded operator-readable completion detail.", "Pending offline operation applied",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 60).build(),
                        runtimeField("operation_id", SchemaValueType.STRING,
                                "Stable operation identity used for exact replay suppression.", "offline/fixture/1",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 10).build(),
                        runtimeField("operation_type", SchemaValueType.STRING,
                                "Bounded operation family.", "offline",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 20).build(),
                        runtimeField("resulting_revision", SchemaValueType.INTEGER,
                                "Transaction revision that contains the completed operation.", "12",
                                CoreDiagnostics.STALE_TRANSACTION_STATE, 40).build(),
                        runtimeField("transaction_id", SchemaValueType.STRING,
                                "Transaction identity associated with the operation.", "00000000-0000-0000-0000-000000000003",
                                CoreDiagnostics.TRANSACTION_LEDGER_FULL, 30).build()
                )
        );
    }

    private static SchemaDescriptor pendingProgressionOperation() {
        return schema(
                "pending_progression_operation",
                SchemaAudience.INTERNAL,
                "Pending offline progression operation",
                "Version- and definition-pinned operation applied only while the target player is online.",
                List.of(
                        runtimeField("balance_id", SchemaValueType.RESOURCE_LOCATION,
                                "Canonical balance definition identity.", "mypack:renown",
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 70).build(),
                        runtimeField("created_at", SchemaValueType.INTEGER,
                                "Authoritative creation epoch milliseconds.", "1784203200000",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 35).build(),
                        runtimeField("definition_digest", SchemaValueType.STRING,
                                "Pinned lowercase SHA-256 definition digest.", "a".repeat(64),
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 60).build(),
                        runtimeField("definition_generation", SchemaValueType.INTEGER,
                                "Pinned live definition generation.", "7",
                                CoreDiagnostics.STALE_TRANSACTION_DEFINITION, 50).build(),
                        runtimeField("delta", SchemaValueType.INTEGER,
                                "Checked signed balance delta.", "25",
                                CoreDiagnostics.BALANCE_TRANSACTION_REJECTED, 80).build(),
                        runtimeField("expires_at", SchemaValueType.INTEGER,
                                "Hard expiry epoch milliseconds after which the operation is quarantined.", "1784289600000",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 40).build(),
                        runtimeField("issuer_id", SchemaValueType.STRING,
                                "Authoritative issuer UUID.", "00000000-0000-0000-0000-000000000001",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 20).build(),
                        runtimeField("operation_id", SchemaValueType.STRING,
                                "Stable queue and receipt identity.", "offline/fixture/1",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 10).build(),
                        runtimeOptionalField("last_attempt_id", SchemaValueType.STRING,
                                "Login-attempt UUID retained until later receipt confirmation.",
                                "00000000-0000-0000-0000-000000000004",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 100).build(),
                        runtimeField("maximum", SchemaValueType.INTEGER,
                                "Inclusive checked post-mutation ceiling.", "1000",
                                CoreDiagnostics.BALANCE_TRANSACTION_REJECTED, 90).build(),
                        runtimeField("minimum", SchemaValueType.INTEGER,
                                "Inclusive checked post-mutation floor.", "0",
                                CoreDiagnostics.BALANCE_TRANSACTION_REJECTED, 85).build(),
                        runtimeOptionalField("quarantine_reason", SchemaValueType.STRING,
                                "Bounded evidence when the operation cannot apply safely.", "Pending operation expired",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 110).build(),
                        runtimeField("reward_eligible", SchemaValueType.BOOLEAN,
                                "Whether the operation may participate in an explicitly allowed reward path.", "false",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 80).build(),
                        runtimeField("status", SchemaValueType.ENUM,
                                "Pending or quarantined queue state.", "PENDING",
                                CoreDiagnostics.OFFLINE_OPERATION_QUARANTINED, 95)
                                .allowedValues("PENDING", "QUARANTINED").build(),
                        runtimeField("target_id", SchemaValueType.STRING,
                                "Target player UUID; no offline player NBT is opened.", "00000000-0000-0000-0000-000000000002",
                                CoreDiagnostics.PLAYER_DATA_IDENTITY_MISMATCH, 30).build()
                )
        );
    }

    private static SchemaDescriptor playerDataSnapshot() {
        return schema(
                "player_data_snapshot",
                SchemaAudience.INTERNAL,
                "Player data snapshot",
                "Atomically written, reread, and digest-verified recovery envelope for one attachment.",
                List.of(
                        runtimeField("player_data", SchemaValueType.OBJECT,
                                "Bounded serialized player attachment.", "{ data_version = 2 }",
                                CoreDiagnostics.PLAYER_DATA_QUARANTINED, 40).build(),
                        runtimeField("created_at", SchemaValueType.INTEGER,
                                "Authoritative snapshot epoch milliseconds.", "1784203200000",
                                CoreDiagnostics.SNAPSHOT_EXPORT_FAILED, 30).build(),
                        runtimeField("player_data_digest", SchemaValueType.STRING,
                                "SHA-256 digest verified after the atomic write.", "a".repeat(64),
                                CoreDiagnostics.SNAPSHOT_EXPORT_FAILED, 50).build(),
                        runtimeField("snapshot_version", SchemaValueType.INTEGER,
                                "Snapshot envelope contract version.", "1",
                                CoreDiagnostics.SNAPSHOT_EXPORT_FAILED, 10).build(),
                        runtimeField("player_id", SchemaValueType.STRING,
                                "UUID whose attachment is enclosed.", "00000000-0000-0000-0000-000000000001",
                                CoreDiagnostics.PLAYER_DATA_IDENTITY_MISMATCH, 20).build()
                )
        );
    }

    private static SchemaDescriptor networkHandshake() {
        return schema(
                "network_handshake",
                SchemaAudience.INTERNAL,
                "Network handshake",
                "Connection-scoped protocol, feature, server identity, and semantic/presentation revision contract.",
                List.of(
                        networkField("definition_generation", SchemaValueType.INTEGER,
                                "Monotonic server gameplay-definition generation.", "7",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 50).build(),
                        networkField("features", SchemaValueType.INTEGER,
                                "Required bounded protocol feature bitset.", "15",
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 40).build(),
                        networkField("presentation_digest", SchemaValueType.STRING,
                                "SHA-256 of the exact sanitized definition projection bytes.", "b".repeat(64),
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 70).build(),
                        networkField("presentation_revision", SchemaValueType.INTEGER,
                                "Monotonic presentation generation negotiated independently.", "7",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 60).build(),
                        networkField("protocol_version", SchemaValueType.INTEGER,
                                "ProgressiveSkills application protocol version.", "1",
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 30).build(),
                        networkField("semantic_digest", SchemaValueType.STRING,
                                "SHA-256 of the authoritative gameplay definition snapshot.", "a".repeat(64),
                                CoreDiagnostics.NETWORK_STALE_REVISION, 55).build(),
                        networkField("server_identity", SchemaValueType.STRING,
                                "Persistent world/server UUID that scopes the local definition cache.",
                                "00000000-0000-0000-0000-000000000601",
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 10).build(),
                        networkField("session_id", SchemaValueType.STRING,
                                "Ephemeral connection session UUID required on every later payload.",
                                "00000000-0000-0000-0000-000000000602",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 20).build()
                )
        );
    }

    private static SchemaDescriptor definitionProjection() {
        return schema(
                "definition_projection",
                SchemaAudience.INTERNAL,
                "Sanitized definition projection",
                "Client-safe typed definition identity and presentation; gameplay fields and provenance are absent.",
                List.of(
                        networkField("description", SchemaValueType.COMPONENT,
                                "Optional localized description with bounded fallback.",
                                "{ key = \"skill.mypack.physique.desc\", fallback = \"Raw power.\" }",
                                CoreDiagnostics.INVALID_COMPONENT, 30).build(),
                        networkField("display", SchemaValueType.COMPONENT,
                                "Optional localized display component with bounded fallback.",
                                "{ key = \"skill.mypack.physique\", fallback = \"Physique\" }",
                                CoreDiagnostics.INVALID_COMPONENT, 20).build(),
                        networkField("icon", SchemaValueType.ICON,
                                "Optional bounded icon, fallback, alt text, and narration.",
                                "{ type = \"item\", value = \"minecraft:iron_chestplate\" }",
                                CoreDiagnostics.INVALID_ICON, 40).build(),
                        networkField("key", SchemaValueType.STRING,
                                "Typed definition kind and namespaced identity.",
                                "progressiveskills:skill[mypack:physique]",
                                CoreDiagnostics.INVALID_ID, 10).build(),
                        networkField("search_aliases", SchemaValueType.LIST,
                                "Bounded presentation-only search terms.", "[\"Strength\", \"Might\"]",
                                CoreDiagnostics.INVALID_COMPONENT, 50).build()
                )
        );
    }

    private static SchemaDescriptor transferEnvelope() {
        return schema(
                "transfer_envelope",
                SchemaAudience.INTERNAL,
                "Bounded transfer envelope",
                "Atomic compressed definition/full-state transfer split below conservative clientbound ceilings.",
                List.of(
                        networkField("chunk_count", SchemaValueType.INTEGER,
                                "Declared total chunk count checked before allocation.", "4",
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 60).build(),
                        networkField("compressed_bytes", SchemaValueType.INTEGER,
                                "Total compressed bytes under the hard aggregate cap.", "49152",
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 50).build(),
                        networkField("digest", SchemaValueType.STRING,
                                "SHA-256 of the uncompressed payload verified before activation.", "c".repeat(64),
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 70).build(),
                        networkField("kind", SchemaValueType.ENUM,
                                "Closed transfer family.", "definitions",
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 30)
                                .allowedValues("definitions", "full_state").build(),
                        networkField("session_id", SchemaValueType.STRING,
                                "Owning negotiated session UUID.",
                                "00000000-0000-0000-0000-000000000602",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 10).build(),
                        networkField("transfer_id", SchemaValueType.STRING,
                                "Unique transfer UUID used by every chunk and ACK.",
                                "00000000-0000-0000-0000-000000000603",
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 20).build(),
                        networkField("uncompressed_bytes", SchemaValueType.INTEGER,
                                "Expected output bytes bounded before decompression.", "65536",
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 40).build()
                )
        );
    }

    private static SchemaDescriptor visiblePlayerState() {
        return schema(
                "visible_player_state",
                SchemaAudience.INTERNAL,
                "Visible player state",
                "Owner-only authoritative state projection without durable ledgers, provenance, or hidden definitions.",
                List.of(
                        networkField("balances", SchemaValueType.MAP,
                                "Bounded namespaced visible balances.", "{ \"mypack:points\" = 4 }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 70).build(),
                        networkField("definition_generation", SchemaValueType.INTEGER,
                                "Pinned gameplay definition generation.", "7",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 40).build(),
                        networkField("effective_values", SchemaValueType.MAP,
                                "Bounded effective values needed by current client presentation.",
                                "{ \"minecraft:attribute[minecraft:generic.max_health]\" = 4 }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 80).build(),
                        networkField("operation_receipt_count", SchemaValueType.INTEGER,
                                "Visible diagnostic count without receipt contents.", "1",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 100).build(),
                        networkField("orphan_count", SchemaValueType.INTEGER,
                                "Visible diagnostic count without orphan payload contents.", "0",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 90).build(),
                        networkField("player_id", SchemaValueType.STRING,
                                "UUID of the session owner.", "00000000-0000-0000-0000-000000000601",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 10).build(),
                        networkField("presentation_revision", SchemaValueType.INTEGER,
                                "Pinned sanitized presentation generation.", "7",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 50).build(),
                        networkField("semantic_digest", SchemaValueType.STRING,
                                "Pinned gameplay SHA-256.", "a".repeat(64),
                                CoreDiagnostics.NETWORK_STALE_REVISION, 45).build(),
                        networkField("state_revision", SchemaValueType.INTEGER,
                                "Authoritative transaction compare-and-swap revision.", "3",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 30).build(),
                        networkField("storage_revision", SchemaValueType.INTEGER,
                                "Monotonic visible-state continuity revision.", "5",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 20).build()
                )
        );
    }

    private static SchemaDescriptor stateDelta() {
        return schema(
                "state_delta",
                SchemaAudience.INTERNAL,
                "Visible state delta",
                "Typed changed/removed paths applied only across an exact base-to-new revision edge.",
                List.of(
                        networkField("base_revision", SchemaValueType.INTEGER,
                                "Required current client storage revision.", "5",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 10).build(),
                        networkField("changed_balances", SchemaValueType.MAP,
                                "Changed or added visible balance paths.", "{ \"mypack:points\" = 5 }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 40).build(),
                        networkField("changed_effective_values", SchemaValueType.MAP,
                                "Changed or added effective-value paths.", "{}",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 60).build(),
                        networkField("new_revision", SchemaValueType.INTEGER,
                                "Strictly newer resulting storage revision.", "6",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 20).build(),
                        networkField("removed_balances", SchemaValueType.LIST,
                                "Removed visible balance paths.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 50).build(),
                        networkField("removed_effective_values", SchemaValueType.LIST,
                                "Removed effective-value paths.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 70).build(),
                        networkField("resulting_state_digest", SchemaValueType.STRING,
                                "SHA-256 of the exact post-application full visible state.", "d".repeat(64),
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 80).build()
                )
        );
    }

    private static SchemaDescriptor networkIntent() {
        return schema(
                "network_intent",
                SchemaAudience.INTERNAL,
                "Bounded client intent",
                "Serverbound request identity and stale guards; clients never provide costs, XP, or effect amounts.",
                List.of(
                        runtimeField("definition_generation", SchemaValueType.INTEGER,
                                "Client-observed gameplay definition generation.", "7",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 30).build(),
                        runtimeField("intent_type", SchemaValueType.ENUM,
                                "Closed server-registered intent family.", "noop_test",
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 60)
                                .allowedValues("noop_test").build(),
                        runtimeField("payload", SchemaValueType.STRING,
                                "Small type-specific bounded selection payload; never effect amounts or commands.", "\"\"",
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 70).build(),
                        runtimeField("request_id", SchemaValueType.INTEGER,
                                "Monotonic request id covered by the bounded replay/result window.", "12",
                                CoreDiagnostics.NETWORK_RATE_LIMITED, 20).build(),
                        runtimeField("semantic_digest", SchemaValueType.STRING,
                                "Client-observed gameplay SHA-256.", "a".repeat(64),
                                CoreDiagnostics.NETWORK_STALE_REVISION, 40).build(),
                        runtimeField("session_id", SchemaValueType.STRING,
                                "Current connection session UUID.",
                                "00000000-0000-0000-0000-000000000602",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 10).build(),
                        runtimeField("state_revision", SchemaValueType.INTEGER,
                                "Client-observed authoritative state revision.", "3",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 50).build()
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

    private static FieldDescriptor.Builder runtimeOptionalField(
            String path,
            SchemaValueType type,
            String description,
            String example,
            DiagnosticCode diagnostic,
            int order
    ) {
        return field(path, type, false, description, example, diagnostic, EditorWidget.OBJECT, order)
                .projection(ProjectionPolicy.SERVER_ONLY);
    }

    private static FieldDescriptor.Builder networkField(
            String path,
            SchemaValueType type,
            String description,
            String example,
            DiagnosticCode diagnostic,
            int order
    ) {
        return field(path, type, true, description, example, diagnostic, EditorWidget.OBJECT, order)
                .projection(ProjectionPolicy.CLIENT_VISIBLE);
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
