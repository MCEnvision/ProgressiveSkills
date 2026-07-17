package com.envisione.progressiveskills.common.schema;

import com.envisione.progressiveskills.common.ability.AbilityActionType;
import com.envisione.progressiveskills.common.ability.AbilityCostType;
import com.envisione.progressiveskills.common.ability.AbilityEffectType;
import com.envisione.progressiveskills.common.ability.AbilityKind;
import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.classdef.ClassSpellLearningPolicy;
import com.envisione.progressiveskills.common.classdef.ClassSwapPolicy;
import com.envisione.progressiveskills.common.diagnostic.CoreDiagnostics;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticCode;
import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.SchemaVersion;
import com.envisione.progressiveskills.common.network.NetworkLimits;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.CurveRounding;
import com.envisione.progressiveskills.common.skill.CurveType;
import com.envisione.progressiveskills.common.rule.FakePlayerPolicy;
import com.envisione.progressiveskills.common.rule.BlockOrigin;
import com.envisione.progressiveskills.common.rule.RuleMultiplierMode;
import com.envisione.progressiveskills.common.rule.RuleMultiplierStage;
import com.envisione.progressiveskills.common.rule.RuleStackRule;
import com.envisione.progressiveskills.common.requirement.ComparisonOperator;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionStatus;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import com.envisione.progressiveskills.common.tree.TreeDependencyPolicy;
import com.envisione.progressiveskills.common.tree.TreeScope;
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
        builder.register(treeDefinition());
        builder.register(treeNode());
        builder.register(classSlotDefinition());
        builder.register(classDefinition());
        builder.register(classGrant());
        builder.register(classSynergy());
        builder.register(abilityDefinition());
        builder.register(abilityPersistentEffect());
        builder.register(abilityCost());
        builder.register(abilityTargeting());
        builder.register(abilityAction());
        builder.register(ruleDefinition());
        builder.register(requirementExpression());
        builder.register(numericExpression());
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
        builder.register(paidCostRecord());
        builder.register(treeIntent());
        builder.register(treeRefundPreview());
        builder.register(classIntent());
        builder.register(classChangePreview());
        builder.register(visibleClassSelection());
        builder.register(abilityIntent());
        builder.register(visibleAbilityState());
        builder.register(visibleAbilitySlot());
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

    private static SchemaDescriptor treeDefinition() {
        return schema(
                "tree_definition",
                SchemaAudience.AUTHORING,
                "Tree definition",
                "Bounded single rank Core progression tree with exact currency costs and cascade refunds.",
                List.of(
                        field("bind", SchemaValueType.RESOURCE_LOCATION, false,
                                "Skill identity required for skill scope and forbidden for global scope.",
                                "mypack:mining",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.RESOURCE_LOCATION, 70).build(),
                        field("currency", SchemaValueType.RESOURCE_LOCATION, true,
                                "Named character currency debited by node purchases and restored by exact refunds.",
                                "progressiveskills:global_points",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.RESOURCE_LOCATION, 80).build(),
                        field("dependency_policy", SchemaValueType.ENUM, false,
                                "Policy used when refunding a node with owned transitive dependents.",
                                "cascade_refund",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.SELECT, 90)
                                .allowedValues(Arrays.stream(TreeDependencyPolicy.values())
                                        .map(TreeDependencyPolicy::serializedName).toArray(String[]::new))
                                .defaultString("cascade_refund").omitWhenDefault().build(),
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized tree description.",
                                "{ fallback = \"A practical mining specialization.\" }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.COMPONENT, 30).build(),
                        field("display", SchemaValueType.COMPONENT, true,
                                "Localized tree name used by commands and presentation.",
                                "{ fallback = \"Mining Paths\" }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.COMPONENT, 20).build(),
                        field("enabled", SchemaValueType.BOOLEAN, false,
                                "Whether the tree accepts purchases and retains valid ownership during reconciliation. Refunds remain available.",
                                "true",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.CHECKBOX, 50)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("icon", SchemaValueType.ICON, true,
                                "Tree icon with fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:iron_pickaxe\", fallback = \"minecraft:barrier\", alt = \"Iron pickaxe\" }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.ICON, 40).build(),
                        field("nodes", SchemaValueType.LIST, true,
                                "One to sixty four stable acyclic node entries merged by node id.",
                                "[{ id = \"mypack:mining/root\", cost = 1, row = 0, col = 0 }]",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.LIST, 100)
                                .diff(DiffPolicy.MERGE_BY_KEY).build(),
                        field("scope", SchemaValueType.ENUM, true,
                                "Global tree or tree bound to one skill.",
                                "skill",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.SELECT, 60)
                                .allowedValues(Arrays.stream(TreeScope.values())
                                        .map(TreeScope::serializedName).toArray(String[]::new))
                                .build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded alternate terms used by tree search.",
                                "[\"Mining\", \"Ore\"]",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.LIST, 45)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build()
                )
        );
    }

    private static SchemaDescriptor treeNode() {
        return schema(
                "tree_node",
                SchemaAudience.AUTHORING,
                "Tree node",
                "Stable single rank node with bounded prerequisites, exact cost, and persistent attribute grants.",
                List.of(
                        field("col", SchemaValueType.INTEGER, true,
                                "Horizontal grid coordinate between negative and positive four thousand ninety six.",
                                "1",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.INTEGER, 70).build(),
                        field("cost", SchemaValueType.INTEGER, true,
                                "Positive named currency amount recorded exactly when purchased.",
                                "3",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.INTEGER, 50).build(),
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized node description.",
                                "{ fallback = \"Improves mining endurance.\" }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.COMPONENT, 25).build(),
                        field("display", SchemaValueType.COMPONENT, true,
                                "Localized node name.",
                                "{ fallback = \"Stone Sense\" }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.COMPONENT, 20).build(),
                        field("grants", SchemaValueType.LIST, false,
                                "Up to thirty two stable persistent attribute grants merged by grant id.",
                                "[{ id = \"mypack:mining/root/toughness\", type = \"attribute\", attribute = \"minecraft:generic.armor\", operation = \"add_value\", value = 1.0 }]",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.LIST, 120)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("grants.attribute", SchemaValueType.RESOURCE_LOCATION, true,
                                "Registered player attribute targeted by this grant.",
                                "minecraft:generic.armor",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.RESOURCE_LOCATION, 123).build(),
                        field("grants.id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Stable source identity for this persistent grant.",
                                "mypack:mining/root/toughness",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.RESOURCE_LOCATION, 121).build(),
                        field("grants.operation", SchemaValueType.ENUM, true,
                                "Supported deterministic attribute operation.",
                                "add_value",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.SELECT, 124)
                                .allowedValues(Arrays.stream(AttributeOperation.values())
                                        .map(AttributeOperation::serializedName).toArray(String[]::new))
                                .build(),
                        field("grants.type", SchemaValueType.ENUM, true,
                                "Core tree grant type.",
                                "attribute",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.SELECT, 122)
                                .allowedValues("attribute").build(),
                        field("grants.value", SchemaValueType.DECIMAL, true,
                                "Nonzero fixed point attribute contribution.",
                                "1.0",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.DECIMAL, 125).build(),
                        field("icon", SchemaValueType.ICON, true,
                                "Node icon with fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:stone\", fallback = \"minecraft:barrier\", alt = \"Stone\" }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.ICON, 30).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Stable node identity unique across the complete live tree catalog.",
                                "mypack:mining/root",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("min_level", SchemaValueType.MAP, false,
                                "Up to thirty two skill ids mapped to nonnegative minimum levels.",
                                "{ \"mypack:mining\" = 5 }",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.KEY_VALUE, 100)
                                .defaultEmptyObject().omitWhenDefault().build(),
                        field("requires", SchemaValueType.LIST, false,
                                "Same tree node ids that must all be owned.",
                                "[\"mypack:mining/root\"]",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.LIST, 80)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("requires_any", SchemaValueType.LIST, false,
                                "Same tree node ids of which at least one must be owned when nonempty.",
                                "[\"mypack:mining/left\", \"mypack:mining/right\"]",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.LIST, 90)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("row", SchemaValueType.INTEGER, true,
                                "Vertical grid coordinate between negative and positive four thousand ninety six.",
                                "0",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.INTEGER, 60).build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded alternate terms used by node search.",
                                "[\"Armor\"]",
                                CoreDiagnostics.INVALID_TREE, EditorWidget.LIST, 40)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build()
                )
        );
    }

    private static SchemaDescriptor classSlotDefinition() {
        return schema(
                "class_slot_definition",
                SchemaAudience.AUTHORING,
                "Class slot definition",
                "Named weighted capacity bucket used by Core class selection and swap policy.",
                List.of(
                        field("capacity", SchemaValueType.INTEGER, true,
                                "Positive capacity from one through sixty four.", "2",
                                CoreDiagnostics.INVALID_CLASS_SLOT, EditorWidget.INTEGER, 50).build(),
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized class slot description.",
                                "{ fallback = \"Combat specializations.\" }",
                                CoreDiagnostics.INVALID_CLASS_SLOT, EditorWidget.COMPONENT, 30).build(),
                        field("display", SchemaValueType.COMPONENT, false,
                                "Optional localized class slot name.",
                                "{ fallback = \"Combat\" }",
                                CoreDiagnostics.INVALID_CLASS_SLOT, EditorWidget.COMPONENT, 20).build(),
                        field("icon", SchemaValueType.ICON, false,
                                "Optional class slot icon with alternative text.",
                                "{ type = \"item\", value = \"minecraft:iron_sword\", fallback = \"minecraft:barrier\", alt = \"Iron sword\" }",
                                CoreDiagnostics.INVALID_CLASS_SLOT, EditorWidget.ICON, 40).build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded alternate terms for later class search.", "[\"Role\"]",
                                CoreDiagnostics.INVALID_CLASS_SLOT, EditorWidget.LIST, 45)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("swap_policy", SchemaValueType.ENUM, false,
                                "Whether an atomic confirmed replacement is permitted in this slot.",
                                "allowed", CoreDiagnostics.INVALID_CLASS_SLOT, EditorWidget.SELECT, 60)
                                .allowedValues(Arrays.stream(ClassSwapPolicy.values())
                                        .map(ClassSwapPolicy::serializedName).toArray(String[]::new))
                                .defaultString("allowed").omitWhenDefault().build()
                ),
                List.of(SchemaConstraint.requiredTogether(
                        CoreDiagnostics.INVALID_CLASS_SLOT,
                        "Class slot display and icon are declared together.", "display", "icon"))
        );
    }

    private static SchemaDescriptor classDefinition() {
        return schema(
                "class_definition",
                SchemaAudience.AUTHORING,
                "Class definition",
                "Bounded Core class with weighted slot use, prerequisites, costs, grants, and receipt protected starter kit.",
                List.of(
                        field("access_required", SchemaValueType.BOOLEAN, false,
                                "Require a source owned class access entitlement before selection.", "false",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.CHECKBOX, 55)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized class description.", "{ fallback = \"Arcane specialist.\" }",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.COMPONENT, 30).build(),
                        field("display", SchemaValueType.COMPONENT, true,
                                "Localized class name.", "{ fallback = \"Mage\" }",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.COMPONENT, 20).build(),
                        field("enabled", SchemaValueType.BOOLEAN, false,
                                "Whether new selections are accepted and retained prerequisites may remain active.",
                                "true", CoreDiagnostics.INVALID_CLASS, EditorWidget.CHECKBOX, 50)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("exclusive_tags", SchemaValueType.LIST, false,
                                "Stable coexistence tags that cannot overlap another selected class.",
                                "[\"mypack:arcane_primary\"]",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.LIST, 80)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("grants", SchemaValueType.LIST, false,
                                "Up to thirty two source owned persistent grants merged by stable grant id.",
                                "[{ id = \"mypack:mage/tree\", type = \"tree_access\", tree = \"mypack:arcane_tree\" }]",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.LIST, 150)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("icon", SchemaValueType.ICON, true,
                                "Class icon with fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:enchanted_book\", fallback = \"minecraft:barrier\", alt = \"Enchanted book\" }",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.ICON, 40).build(),
                        field("prerequisites.classes", SchemaValueType.LIST, false,
                                "Selected classes that must all remain active.", "[\"mypack:apprentice\"]",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.LIST, 110)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("prerequisites.min_level", SchemaValueType.MAP, false,
                                "Up to thirty two skill ids mapped to nonnegative minimum levels.",
                                "{ \"mypack:arcana\" = 15 }",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.KEY_VALUE, 90)
                                .defaultEmptyObject().omitWhenDefault().build(),
                        field("prerequisites.nodes", SchemaValueType.LIST, false,
                                "Owned Core tree nodes that must all remain owned.",
                                "[\"mypack:arcane/root\"]",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.LIST, 100)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("respec_allowed", SchemaValueType.BOOLEAN, false,
                                "Whether a selected class may be removed by player respec or swap.", "true",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.CHECKBOX, 130)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("respec_cost", SchemaValueType.OBJECT, false,
                                "Optional named currency charge for an allowed removal.",
                                "{ currency = \"progressiveskills:global_points\", amount = 2 }",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.OBJECT, 140).build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded alternate class search terms.", "[\"Caster\"]",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.LIST, 45)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("selection_cost", SchemaValueType.OBJECT, false,
                                "Optional named currency charge sunk on selection and never refunded implicitly.",
                                "{ currency = \"progressiveskills:global_points\", amount = 5 }",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.OBJECT, 120).build(),
                        field("slot", SchemaValueType.RESOURCE_LOCATION, true,
                                "Pack defined class slot consumed by this class.", "mypack:combat",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.RESOURCE_LOCATION, 60).build(),
                        field("slot_cost", SchemaValueType.INTEGER, true,
                                "Weighted slot use. Zero represents an explicit background class.", "1",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.INTEGER, 70).build(),
                        field("starter_kit", SchemaValueType.LIST, false,
                                "Up to thirty two item ids delivered only on the first successful selection receipt.",
                                "[\"minecraft:book\"]",
                                CoreDiagnostics.INVALID_CLASS, EditorWidget.LIST, 160)
                                .defaultEmptyList().diff(DiffPolicy.ORDERED).omitWhenDefault().build(),
                        field("synergy", SchemaValueType.LIST, false,
                                "Named bounded synergy definitions nested under their owning class.",
                                "[{ id = \"mypack:spellblade\", requires_classes = [\"mypack:mage\", \"mypack:warrior\"] }]",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.LIST, 170)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build()
                )
        );
    }

    private static SchemaDescriptor classGrant() {
        return schema(
                "class_grant",
                SchemaAudience.AUTHORING,
                "Class persistent grant",
                "Source owned attribute, ability, spell, stage, tree access, or class access contribution.",
                List.of(
                        field("ability", SchemaValueType.RESOURCE_LOCATION, false,
                                "Ability target required by ability grants.", "mypack:arcane_surge",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 60).build(),
                        field("attribute", SchemaValueType.RESOURCE_LOCATION, false,
                                "Attribute target required by attribute grants.",
                                "minecraft:generic.max_health",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 30).build(),
                        field("class", SchemaValueType.RESOURCE_LOCATION, false,
                                "Class access target required by class access grants.", "mypack:berserker",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 80).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Globally unique stable grant source identity.", "mypack:mage/arcane_tree",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("learning", SchemaValueType.ENUM, false,
                                "Reversible spell learning satisfaction policy.", "require_existing",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.SELECT, 110)
                                .allowedValues(Arrays.stream(ClassSpellLearningPolicy.values())
                                        .map(ClassSpellLearningPolicy::serializedName).toArray(String[]::new))
                                .defaultString("require_existing").omitWhenDefault().build(),
                        field("level", SchemaValueType.INTEGER, false,
                                "Bounded virtual spell level from one through two hundred fifty five.", "3",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.INTEGER, 100)
                                .defaultInteger(1).omitWhenDefault().build(),
                        field("operation", SchemaValueType.ENUM, false,
                                "Deterministic attribute operation required by attribute grants.", "add_value",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.SELECT, 40)
                                .allowedValues(Arrays.stream(AttributeOperation.values())
                                        .map(AttributeOperation::serializedName).toArray(String[]::new)).build(),
                        field("selection", SchemaValueType.ENUM, false,
                                "Core spell selection ownership mode.", "virtual_source",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.SELECT, 90)
                                .allowedValues("virtual_source")
                                .defaultString("virtual_source").omitWhenDefault().build(),
                        field("spell", SchemaValueType.RESOURCE_LOCATION, false,
                                "Spell target required by spell grants.", "irons_spellbooks:fireball",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 85).build(),
                        field("stage", SchemaValueType.RESOURCE_LOCATION, false,
                                "Stage target required by stage grants.", "mypack:arcane_access",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 70).build(),
                        field("tree", SchemaValueType.RESOURCE_LOCATION, false,
                                "Tree target required by tree access grants.", "mypack:arcane_tree",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.RESOURCE_LOCATION, 75).build(),
                        field("type", SchemaValueType.ENUM, true,
                                "Closed Core class grant type.", "tree_access",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.SELECT, 20)
                                .allowedValues(Arrays.stream(ClassGrantType.values())
                                        .map(ClassGrantType::serializedName).toArray(String[]::new)).build(),
                        field("value", SchemaValueType.DECIMAL, false,
                                "Nonzero fixed point attribute value. Other typed values are derived.", "2.0",
                                CoreDiagnostics.CLASS_ENTITLEMENT_INVALID, EditorWidget.DECIMAL, 50).build()
                )
        );
    }

    private static SchemaDescriptor classSynergy() {
        return schema(
                "class_synergy",
                SchemaAudience.AUTHORING,
                "Class synergy",
                "Named source owned grants active only while every required selected class remains active.",
                List.of(
                        field("description", SchemaValueType.COMPONENT, false,
                                "Optional localized synergy description.",
                                "{ fallback = \"Arcane martial training.\" }",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.COMPONENT, 40).build(),
                        field("display", SchemaValueType.COMPONENT, false,
                                "Optional localized synergy name.", "{ fallback = \"Spellblade\" }",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.COMPONENT, 30).build(),
                        field("enabled", SchemaValueType.BOOLEAN, false,
                                "Whether the synergy contributes grants when every requirement is active.", "true",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.CHECKBOX, 60)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("grants", SchemaValueType.LIST, true,
                                "One through thirty two globally unique class grant entries.",
                                "[{ id = \"mypack:spellblade/stance\", type = \"ability\", ability = \"mypack:spellblade_stance\" }]",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.LIST, 80)
                                .diff(DiffPolicy.MERGE_BY_KEY).build(),
                        field("icon", SchemaValueType.ICON, false,
                                "Optional synergy icon with alternative text.",
                                "{ type = \"item\", value = \"minecraft:golden_sword\", fallback = \"minecraft:barrier\", alt = \"Golden sword\" }",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.ICON, 50).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Globally unique stable synergy identity.", "mypack:spellblade",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("requires_classes", SchemaValueType.LIST, true,
                                "Two through sixteen known classes that must all remain active.",
                                "[\"mypack:mage\", \"mypack:warrior\"]",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.LIST, 70)
                                .diff(DiffPolicy.SET).build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded alternate synergy terms.", "[\"Hybrid\"]",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, EditorWidget.LIST, 55)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build()
                ),
                List.of(SchemaConstraint.requiredTogether(
                        CoreDiagnostics.INVALID_CLASS_SYNERGY,
                        "Synergy display and icon are declared together.", "display", "icon"))
        );
    }

    private static SchemaDescriptor abilityDefinition() {
        return schema(
                "ability_definition",
                SchemaAudience.AUTHORING,
                "Ability definition",
                "Bounded Core passive, toggle, or active behavior assigned through fixed registered slots.",
                List.of(
                        field("actions", SchemaValueType.LIST, false,
                                "Authored order of up to sixteen active message, heal, or vanilla effect actions.",
                                "[{ id = \"mypack:second_wind/heal\", type = \"heal\", amount = 4.0 }]",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.LIST, 160)
                                .defaultEmptyList().diff(DiffPolicy.ORDERED).omitWhenDefault().build(),
                        field("cooldown_group", SchemaValueType.RESOURCE_LOCATION, false,
                                "Shared cooldown identity. Omission uses the ability id.", "mypack:recovery",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.RESOURCE_LOCATION, 120).build(),
                        field("cooldown_ticks", SchemaValueType.INTEGER, false,
                                "Nonnegative activation cooldown up to seventy two thousand ticks.", "400",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.INTEGER, 125)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("costs", SchemaValueType.LIST, false,
                                "Up to three stable named currency, hunger, or experience activation costs.",
                                "[{ id = \"mypack:second_wind/hunger\", type = \"hunger\", amount = 4 }]",
                                CoreDiagnostics.INVALID_ABILITY_COST, EditorWidget.LIST, 140)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("default_on", SchemaValueType.BOOLEAN, false,
                                "Initial enabled state for an owned toggle ability.", "false",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.CHECKBOX, 100)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("description", SchemaValueType.COMPONENT, false,
                                "Localized ability description.", "{ fallback = \"Recover health.\" }",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.COMPONENT, 30).build(),
                        field("display", SchemaValueType.COMPONENT, true,
                                "Localized ability name.", "{ fallback = \"Second Wind\" }",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.COMPONENT, 20).build(),
                        field("enabled", SchemaValueType.BOOLEAN, false,
                                "Whether ownership may become usable under the current definition.", "true",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.CHECKBOX, 60)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("icon", SchemaValueType.ICON, true,
                                "Ability icon with fallback and alternative text.",
                                "{ type = \"item\", value = \"minecraft:shield\", fallback = \"minecraft:barrier\", alt = \"Shield\" }",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.ICON, 40).build(),
                        field("kind", SchemaValueType.ENUM, true,
                                "Closed Core ability lifecycle.", "active",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.SELECT, 70)
                                .allowedValues(Arrays.stream(AbilityKind.values())
                                        .map(AbilityKind::serializedName).toArray(String[]::new)).build(),
                        field("max_charges", SchemaValueType.INTEGER, false,
                                "Maximum available charges from one through sixteen.", "2",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.INTEGER, 130)
                                .defaultInteger(1).omitWhenDefault().build(),
                        field("persistent_effects", SchemaValueType.LIST, false,
                                "Up to sixteen source owned attribute or boolean flag effects for passive and toggle abilities.",
                                "[{ id = \"mypack:guard/armor\", type = \"attribute\", attribute = \"minecraft:generic.armor\", operation = \"add_value\", value = 2.0 }]",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.LIST, 110)
                                .defaultEmptyList().diff(DiffPolicy.MERGE_BY_KEY).omitWhenDefault().build(),
                        field("recharge_ticks", SchemaValueType.INTEGER, false,
                                "Nonnegative charge recharge duration up to seventy two thousand ticks.", "200",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.INTEGER, 135)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("search_aliases", SchemaValueType.LIST, false,
                                "Bounded alternate ability search terms.", "[\"Recovery\"]",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.LIST, 45)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault().build(),
                        field("slot_allowed", SchemaValueType.BOOLEAN, false,
                                "Whether the runtime ability may be assigned to one of eight fixed action slots.", "true",
                                CoreDiagnostics.INVALID_ABILITY, EditorWidget.CHECKBOX, 80).build(),
                        field("targeting", SchemaValueType.OBJECT, false,
                                "Validated self, entity, or block targeting policy for active abilities.",
                                "{ mode = \"self\" }",
                                CoreDiagnostics.INVALID_ABILITY_TARGET, EditorWidget.OBJECT, 150).build()
                )
        );
    }

    private static SchemaDescriptor abilityPersistentEffect() {
        return schema(
                "ability_persistent_effect",
                SchemaAudience.AUTHORING,
                "Ability persistent effect",
                "Stable source owned attribute or boolean flag contribution active with a passive or enabled toggle.",
                List.of(
                        field("attribute", SchemaValueType.RESOURCE_LOCATION, false,
                                "Attribute target for an attribute effect.", "minecraft:generic.armor",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.RESOURCE_LOCATION, 30).build(),
                        field("flag", SchemaValueType.RESOURCE_LOCATION, false,
                                "Namespaced boolean flag target for a flag effect.", "mypack:guarding",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.RESOURCE_LOCATION, 60).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Stable effect source identity unique within its ability.", "mypack:guard/armor",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("operation", SchemaValueType.ENUM, false,
                                "Deterministic attribute operation.", "add_value",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.SELECT, 40)
                                .allowedValues(Arrays.stream(AttributeOperation.values())
                                        .map(AttributeOperation::serializedName).toArray(String[]::new)).build(),
                        field("type", SchemaValueType.ENUM, true,
                                "Closed Core persistent effect type.", "attribute",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.SELECT, 20)
                                .allowedValues(Arrays.stream(AbilityEffectType.values())
                                        .map(AbilityEffectType::serializedName).toArray(String[]::new)).build(),
                        field("value", SchemaValueType.ANY, true,
                                "Nonzero fixed point attribute value or explicit boolean flag value.", "2.0",
                                CoreDiagnostics.INVALID_ABILITY_EFFECT, EditorWidget.SINGLE_LINE, 50).build()
                )
        );
    }

    private static SchemaDescriptor abilityCost() {
        return schema(
                "ability_cost",
                SchemaAudience.AUTHORING,
                "Ability activation cost",
                "Stable positive literal named currency, vanilla hunger, or vanilla experience cost.",
                List.of(
                        field("amount", SchemaValueType.INTEGER, true,
                                "Positive bounded cost amount.", "4",
                                CoreDiagnostics.INVALID_ABILITY_COST, EditorWidget.INTEGER, 40).build(),
                        field("currency", SchemaValueType.RESOURCE_LOCATION, false,
                                "Existing named currency required only for currency costs.", "progressiveskills:global_points",
                                CoreDiagnostics.INVALID_ABILITY_COST, EditorWidget.RESOURCE_LOCATION, 30).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Stable cost leg identity unique within its ability.", "mypack:surge/points",
                                CoreDiagnostics.INVALID_ABILITY_COST, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("type", SchemaValueType.ENUM, true,
                                "Closed Core cost source.", "currency",
                                CoreDiagnostics.INVALID_ABILITY_COST, EditorWidget.SELECT, 20)
                                .allowedValues(Arrays.stream(AbilityCostType.values())
                                        .map(AbilityCostType::serializedName).toArray(String[]::new)).build()
                )
        );
    }

    private static SchemaDescriptor abilityTargeting() {
        return schema(
                "ability_targeting",
                SchemaAudience.AUTHORING,
                "Ability targeting",
                "Bounded server validated self, entity, or block target contract.",
                List.of(
                        field("line_of_sight", SchemaValueType.BOOLEAN, false,
                                "Require an unobstructed server ray check for a nonself target.", "true",
                                CoreDiagnostics.INVALID_ABILITY_TARGET, EditorWidget.CHECKBOX, 30).build(),
                        field("mode", SchemaValueType.ENUM, false,
                                "Closed Core target mode. Omission selects self.", "entity",
                                CoreDiagnostics.INVALID_ABILITY_TARGET, EditorWidget.SELECT, 10)
                                .allowedValues(Arrays.stream(AbilityTargetMode.values())
                                        .map(AbilityTargetMode::serializedName).toArray(String[]::new))
                                .defaultString("self").omitWhenDefault().build(),
                        field("range", SchemaValueType.INTEGER, false,
                                "Zero for self or one through sixty four blocks for entity and block targets.", "16",
                                CoreDiagnostics.INVALID_ABILITY_TARGET, EditorWidget.INTEGER, 20).build()
                )
        );
    }

    private static SchemaDescriptor abilityAction() {
        return schema(
                "ability_action",
                SchemaAudience.AUTHORING,
                "Ability native action",
                "One ordered message, heal, or vanilla effect action from the Core action subset.",
                List.of(
                        field("ambient", SchemaValueType.BOOLEAN, false,
                                "Vanilla effect ambient rendering flag.", "false",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.CHECKBOX, 80)
                                .defaultBoolean(false).omitWhenDefault().build(),
                        field("amplifier", SchemaValueType.INTEGER, false,
                                "Vanilla effect amplifier from zero through two hundred fifty five.", "0",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.INTEGER, 60)
                                .defaultInteger(0).omitWhenDefault().build(),
                        field("amount", SchemaValueType.DECIMAL, false,
                                "Positive literal heal amount.", "4.0",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.DECIMAL, 40).build(),
                        field("duration_ticks", SchemaValueType.INTEGER, false,
                                "Vanilla effect duration from one through seventy two thousand ticks.", "100",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.INTEGER, 70).build(),
                        field("effect", SchemaValueType.RESOURCE_LOCATION, false,
                                "Vanilla mob effect registry target.", "minecraft:speed",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.RESOURCE_LOCATION, 50).build(),
                        field("id", SchemaValueType.RESOURCE_LOCATION, true,
                                "Stable action identity. Authored list order is execution order.", "mypack:second_wind/heal",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.RESOURCE_LOCATION, 10).build(),
                        field("message", SchemaValueType.COMPONENT, false,
                                "Safe localized chat or action feedback component.", "{ fallback = \"Ready.\" }",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.COMPONENT, 35).build(),
                        field("show_icon", SchemaValueType.BOOLEAN, false,
                                "Show the vanilla effect icon.", "true",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.CHECKBOX, 100)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("show_particles", SchemaValueType.BOOLEAN, false,
                                "Show vanilla effect particles.", "true",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.CHECKBOX, 90)
                                .defaultBoolean(true).omitWhenDefault().build(),
                        field("type", SchemaValueType.ENUM, true,
                                "Closed Core native action type.", "heal",
                                CoreDiagnostics.INVALID_ABILITY_ACTION, EditorWidget.SELECT, 20)
                                .allowedValues(Arrays.stream(AbilityActionType.values())
                                        .map(AbilityActionType::serializedName).toArray(String[]::new)).build()
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
                        field("anti_exploit.allowed_block_origins", SchemaValueType.LIST, false,
                                "Allowed block origins. Omission permits natural and creative placed blocks.",
                                "[\"natural\", \"creative_placed\"]",
                                CoreDiagnostics.INVALID_RULE_ANTI_EXPLOIT, EditorWidget.LIST, 185)
                                .allowedValues(Arrays.stream(BlockOrigin.values())
                                        .map(BlockOrigin::serializedName).toArray(String[]::new))
                                .diff(DiffPolicy.SET).projection(ProjectionPolicy.SERVER_ONLY).build(),
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
                                "Exactly one Phase 9 XP output using rule_amount.",
                                "[{ id = \"mypack:stone/xp\", type = \"xp\", skill = \"mypack:mining\", amount_formula = \"rule_amount\" }]",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.LIST, 120)
                                .diff(DiffPolicy.MERGE_BY_KEY).projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("priority", SchemaValueType.INTEGER, false,
                                "Higher priority wins first and exclusive route selection.", "100",
                                CoreDiagnostics.INVALID_RULE_STACK, EditorWidget.INTEGER, 40)
                                .defaultInteger(0).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements", SchemaValueType.LIST, false,
                                "Flat actor requirement list combined with logical all.",
                                "[{ type = \"skill_level\", subject = \"actor\", missing = false, skill = \"mypack:mining\", op = \">=\", value = 5 }]",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.LIST, 130)
                                .defaultEmptyList().diff(DiffPolicy.SET).omitWhenDefault()
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.currency", SchemaValueType.RESOURCE_LOCATION, false,
                                "Currency target for a currency requirement.", "mypack:points",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.RESOURCE_LOCATION, 136)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.missing", SchemaValueType.BOOLEAN, false,
                                "Result used only when the actor context is unavailable.", "false",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.CHECKBOX, 133)
                                .defaultBoolean(false).omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.op", SchemaValueType.ENUM, false,
                                "Integer comparison operator.", ">=",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.SELECT, 134)
                                .allowedValues(Arrays.stream(ComparisonOperator.values())
                                        .map(ComparisonOperator::serializedName).toArray(String[]::new))
                                .defaultString(">=").omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.skill", SchemaValueType.RESOURCE_LOCATION, false,
                                "Skill target for a skill level requirement.", "mypack:mining",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.RESOURCE_LOCATION, 135)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.subject", SchemaValueType.ENUM, false,
                                "Phase 9 requirement subject.", "actor",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.SELECT, 132)
                                .allowedValues("actor").defaultString("actor").omitWhenDefault()
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.type", SchemaValueType.ENUM, true,
                                "Direct requirement value kind.", "skill_level",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.SELECT, 131)
                                .allowedValues("skill_level", "currency")
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("requirements.value", SchemaValueType.INTEGER, true,
                                "Integer threshold compared with the selected actor value.", "5",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.INTEGER, 137)
                                .projection(ProjectionPolicy.SERVER_ONLY).build(),
                        field("rounding", SchemaValueType.ENUM, false,
                                "One final fixed point rounding policy after every literal multiplier group.", "floor",
                                CoreDiagnostics.INVALID_RULE, EditorWidget.SELECT, 115)
                                .allowedValues(Arrays.stream(ExpressionRounding.values())
                                        .map(ExpressionRounding::serializedName).toArray(String[]::new))
                                .defaultString("floor").omitWhenDefault().projection(ProjectionPolicy.SERVER_ONLY).build(),
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

    private static SchemaDescriptor requirementExpression() {
        return schema(
                "requirement_expression",
                SchemaAudience.INTERNAL,
                "Requirement expression",
                "Bounded typed boolean program with deterministic dependencies and explanations.",
                List.of(
                        internalField("dependencies", SchemaValueType.LIST,
                                "Sorted typed dependency keys.", "[\"skill_level:mypack:mining\"]", 30),
                        internalField("max_depth", SchemaValueType.INTEGER,
                                "Hard expression depth ceiling.", "16", 20),
                        internalField("max_nodes", SchemaValueType.INTEGER,
                                "Hard expression node ceiling.", "64", 10)
                )
        );
    }

    private static SchemaDescriptor numericExpression() {
        return schema(
                "numeric_expression",
                SchemaAudience.INTERNAL,
                "Numeric expression",
                "Exact fixed point program with bounded evaluation and one final rounding step.",
                List.of(
                        internalField("dependencies", SchemaValueType.LIST,
                                "Sorted typed dependency keys.", "[]", 30),
                        internalField("max_depth", SchemaValueType.INTEGER,
                                "Hard expression depth ceiling.", "16", 20),
                        internalField("max_nodes", SchemaValueType.INTEGER,
                                "Hard expression node ceiling.", "64", 10),
                        internalField("rounding", SchemaValueType.STRING,
                                "Final fixed point rounding policy.", "floor", 40)
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
                                "Required bounded protocol feature bitset.",
                                Long.toString(NetworkLimits.REQUIRED_FEATURES),
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 40).build(),
                        networkField("presentation_digest", SchemaValueType.STRING,
                                "SHA-256 of the exact sanitized definition projection bytes.", "b".repeat(64),
                                CoreDiagnostics.NETWORK_TRANSFER_REJECTED, 70).build(),
                        networkField("presentation_revision", SchemaValueType.INTEGER,
                                "Monotonic presentation generation negotiated independently.", "7",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 60).build(),
                        networkField("protocol_version", SchemaValueType.INTEGER,
                                "ProgressiveSkills application protocol version.",
                                Integer.toString(NetworkLimits.PROTOCOL_VERSION),
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
                "Client-safe identity, presentation, disclosed tree graph, class selection semantics, and synergy summaries without authority internals.",
                List.of(
                        networkOptionalField("ability", SchemaValueType.OBJECT,
                                "Bounded ability kind, lifecycle, cost, target, cooldown, charge, effect, and action summaries.",
                                "{ kind = \"active\", slot_allowed = true, cooldown_ticks = 400 }",
                                CoreDiagnostics.INVALID_ABILITY, 65).build(),
                        networkOptionalField("class_definition", SchemaValueType.OBJECT,
                                "Bounded class slot use, disclosed requirements, costs, starter kit, and resolved grant summaries.",
                                "{ slot_id = \"mypack:combat\", slot_cost = 1, enabled = true }",
                                CoreDiagnostics.INVALID_CLASS, 70).build(),
                        networkOptionalField("class_slot", SchemaValueType.OBJECT,
                                "Bounded capacity and swap policy for a class slot definition.",
                                "{ capacity = 2, swap_policy = \"allowed\" }",
                                CoreDiagnostics.INVALID_CLASS_SLOT, 60).build(),
                        networkOptionalField("description", SchemaValueType.COMPONENT,
                                "Optional localized description with bounded fallback.",
                                "{ key = \"skill.mypack.physique.desc\", fallback = \"Raw power.\" }",
                                CoreDiagnostics.INVALID_COMPONENT, 30).build(),
                        networkOptionalField("display", SchemaValueType.COMPONENT,
                                "Optional localized display component with bounded fallback.",
                                "{ key = \"skill.mypack.physique\", fallback = \"Physique\" }",
                                CoreDiagnostics.INVALID_COMPONENT, 20).build(),
                        networkOptionalField("icon", SchemaValueType.ICON,
                                "Optional bounded icon, fallback, alt text, and narration.",
                                "{ type = \"item\", value = \"minecraft:iron_chestplate\" }",
                                CoreDiagnostics.INVALID_ICON, 40).build(),
                        networkField("key", SchemaValueType.STRING,
                                "Typed definition kind and namespaced identity.",
                                "progressiveskills:skill[mypack:physique]",
                                CoreDiagnostics.INVALID_ID, 10).build(),
                        networkField("search_aliases", SchemaValueType.LIST,
                                "Bounded presentation-only search terms.", "[\"Strength\", \"Might\"]",
                                CoreDiagnostics.INVALID_COMPONENT, 50).build(),
                        networkField("class_synergies", SchemaValueType.MAP,
                                "Bounded named class synergy summaries keyed by stable synergy id.",
                                "{ \"mypack:spellblade\" = { required_classes = [\"mypack:mage\", \"mypack:warrior\"] } }",
                                CoreDiagnostics.INVALID_CLASS_SYNERGY, 90).build(),
                        networkOptionalField("tree", SchemaValueType.OBJECT,
                                "Bounded disclosed tree graph without grants or historical paid costs.",
                                "{ scope = \"skill\", currency = \"progressiveskills:global_points\" }",
                                CoreDiagnostics.INVALID_TREE, 80).build()
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
                        networkField("abilities", SchemaValueType.MAP,
                                "Owned ability ids mapped to visible toggle, charge, and cooldown state.",
                                "{ \"mypack:second_wind\" = { charges = 1, maximum_charges = 1 } }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 67).build(),
                        networkField("ability_slots", SchemaValueType.MAP,
                                "Assigned fixed slot numbers mapped to owned ability ids.",
                                "{ 0 = \"mypack:second_wind\" }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 68).build(),
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
                        networkField("node_ranks", SchemaValueType.MAP,
                                "Owned Core node ranks without historical paid cost records.",
                                "{ \"mypack:mining/root\" = 1 }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 85).build(),
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
                        networkField("selected_classes", SchemaValueType.MAP,
                                "Selected class ids mapped to visible slot use and active or suspended state.",
                                "{ \"mypack:mage\" = { slot_id = \"mypack:combat\", slot_cost = 1, activity = \"active\" } }",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 87).build(),
                        networkField("selected_ability_slot", SchemaValueType.INTEGER,
                                "Selected fixed slot from zero through seven or negative one when no slot is selected.",
                                "0", CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 69).build(),
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
                        networkField("changed_abilities", SchemaValueType.MAP,
                                "Changed or added owned visible ability states.", "{}",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 32).build(),
                        networkField("changed_ability_slots", SchemaValueType.MAP,
                                "Changed or added fixed ability slot assignments.", "{}",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 34).build(),
                        networkField("changed_effective_values", SchemaValueType.MAP,
                                "Changed or added effective-value paths.", "{}",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 60).build(),
                        networkField("changed_node_ranks", SchemaValueType.MAP,
                                "Changed or added visible Core node ranks.", "{}",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 62).build(),
                        networkField("changed_selected_classes", SchemaValueType.MAP,
                                "Changed or added visible selected class states.", "{}",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 64).build(),
                        networkField("new_revision", SchemaValueType.INTEGER,
                                "Strictly newer resulting storage revision.", "6",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 20).build(),
                        networkField("removed_balances", SchemaValueType.LIST,
                                "Removed visible balance paths.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 50).build(),
                        networkField("removed_abilities", SchemaValueType.LIST,
                                "Removed visible ability ids.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 42).build(),
                        networkField("removed_ability_slots", SchemaValueType.LIST,
                                "Cleared fixed ability slot numbers.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 44).build(),
                        networkField("removed_effective_values", SchemaValueType.LIST,
                                "Removed effective-value paths.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 70).build(),
                        networkField("removed_node_ranks", SchemaValueType.LIST,
                                "Removed visible Core node ids.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 72).build(),
                        networkField("removed_selected_classes", SchemaValueType.LIST,
                                "Removed selected class ids.", "[]",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 74).build(),
                        networkField("resulting_state_digest", SchemaValueType.STRING,
                                "SHA-256 of the exact post-application full visible state.", "d".repeat(64),
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 80).build(),
                        networkField("selected_ability_slot", SchemaValueType.INTEGER,
                                "Resulting selected fixed slot or negative one for no selection.", "0",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 30).build()
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
                                "Closed server-registered intent family.", "tree_buy",
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 60)
                                .allowedValues(
                                        "noop_test", "tree_buy", "tree_refund_preview", "tree_refund_confirm",
                                        "class_select", "class_respec_preview", "class_respec_confirm",
                                        "class_swap_preview", "class_swap_confirm",
                                        "ability_assign", "ability_unassign", "ability_select",
                                        "ability_toggle", "ability_activate")
                                .build(),
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

    private static SchemaDescriptor paidCostRecord() {
        return schema(
                "paid_cost_record",
                SchemaAudience.INTERNAL,
                "Historical paid cost record",
                "Immutable purchase identity, definition lineage, exact paid balances, and persistent grant sources.",
                List.of(
                        runtimeField("definition_revision", SchemaValueType.OBJECT,
                                "Definition generation and semantic digest active when the purchase committed.",
                                "{ generation = 10, semantic_digest = \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\" }",
                                CoreDiagnostics.PAID_COST_LEDGER_INVALID, 30).build(),
                        runtimeField("instance_id", SchemaValueType.OBJECT,
                                "Owner kind, owner id, purchase id, and one based rank identity.",
                                "{ owner_kind = \"progressiveskills:tree\", owner_id = \"mypack:mining\", purchase_id = \"mypack:mining/root\", rank = 1 }",
                                CoreDiagnostics.PAID_COST_LEDGER_INVALID, 10).build(),
                        runtimeField("owner_lineage", SchemaValueType.STRING,
                                "Lowercase SHA 256 of the tree and node grant lineage that owns this purchase.",
                                "b".repeat(64),
                                CoreDiagnostics.TREE_ORPHANED_PURCHASE, 40).build(),
                        runtimeField("paid_balances", SchemaValueType.MAP,
                                "Up to eight positive exact currency debits preserved for refund.",
                                "{ \"progressiveskills:global_points\" = 3 }",
                                CoreDiagnostics.PAID_COST_LEDGER_INVALID, 50).build(),
                        runtimeField("persistent_sources", SchemaValueType.LIST,
                                "Up to thirty two source owned persistent grants installed by the purchase.",
                                "[\"progressiveskills:tree[mypack:mining]/mypack:mining/root/toughness\"]",
                                CoreDiagnostics.PAID_COST_LEDGER_INVALID, 60).build(),
                        runtimeField("purchase_transaction_id", SchemaValueType.STRING,
                                "Transaction UUID that originally committed the exact payment.",
                                "00000000-0000-0000-0000-000000000710",
                                CoreDiagnostics.PAID_COST_LEDGER_INVALID, 20).build()
                )
        );
    }

    private static SchemaDescriptor treeIntent() {
        return schema(
                "tree_intent",
                SchemaAudience.INTERNAL,
                "Tree mutation intent payload",
                "Bounded serverbound node selection with no client supplied cost, grant, balance, or refund amount.",
                List.of(
                        runtimeField("node_id", SchemaValueType.RESOURCE_LOCATION,
                                "Selected stable node identity interpreted only inside the selected tree.",
                                "mypack:mining/root",
                                CoreDiagnostics.INVALID_TREE, 20).build(),
                        runtimeOptionalField("preview_digest", SchemaValueType.STRING,
                                "Required only when the enclosing network intent is tree_refund_confirm and forbidden otherwise.",
                                "c".repeat(64),
                                CoreDiagnostics.TREE_REFUND_DENIED, 30).build(),
                        runtimeField("tree_id", SchemaValueType.RESOURCE_LOCATION,
                                "Selected stable tree identity.",
                                "mypack:mining",
                                CoreDiagnostics.INVALID_TREE, 10).build()
                )
        );
    }

    private static SchemaDescriptor treeRefundPreview() {
        return schema(
                "tree_refund_preview",
                SchemaAudience.INTERNAL,
                "Tree cascade refund preview payload",
                "Clientbound revision pinned affected nodes, exact historical refunds, blockers, and confirmation digest.",
                List.of(
                        networkField("affected_nodes", SchemaValueType.LIST,
                                "Up to sixty four selected and owned dependent nodes in authoritative reverse topological refund order.",
                                "[\"mypack:mining/deep\", \"mypack:mining/root\"]",
                                CoreDiagnostics.TREE_REFUND_DENIED, 80).build(),
                        networkField("blockers", SchemaValueType.LIST,
                                "Up to sixty four bounded reasons that make confirmation unavailable.",
                                "[]",
                                CoreDiagnostics.TREE_REFUND_DENIED, 110).build(),
                        networkField("definition_generation", SchemaValueType.INTEGER,
                                "Definition generation used to calculate the preview.",
                                "10",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 30).build(),
                        networkField("node_id", SchemaValueType.RESOURCE_LOCATION,
                                "Node selected for cascade refund.",
                                "mypack:mining/root",
                                CoreDiagnostics.TREE_REFUND_DENIED, 70).build(),
                        networkField("preview_digest", SchemaValueType.STRING,
                                "Lowercase SHA 256 covering the selected cascade and historical payment evidence.",
                                "d".repeat(64),
                                CoreDiagnostics.TREE_REFUND_DENIED, 100).build(),
                        networkField("refund_balances", SchemaValueType.MAP,
                                "Up to sixty four exact nonnegative currency totals recovered from affected paid cost records.",
                                "{ \"progressiveskills:global_points\" = 6 }",
                                CoreDiagnostics.PAID_COST_LEDGER_INVALID, 90).build(),
                        networkField("request_id", SchemaValueType.INTEGER,
                                "Serverbound preview request identity returned to the requesting client.",
                                "14",
                                CoreDiagnostics.NETWORK_RATE_LIMITED, 20).build(),
                        networkField("semantic_digest", SchemaValueType.STRING,
                                "Gameplay definition digest used to calculate the preview.",
                                "e".repeat(64),
                                CoreDiagnostics.NETWORK_STALE_REVISION, 40).build(),
                        networkField("session_id", SchemaValueType.STRING,
                                "Current connection session UUID.",
                                "00000000-0000-0000-0000-000000000711",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 10).build(),
                        networkField("state_revision", SchemaValueType.INTEGER,
                                "Authoritative player state revision used to calculate the preview.",
                                "8",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 50).build(),
                        networkField("tree_id", SchemaValueType.RESOURCE_LOCATION,
                                "Tree containing every affected purchase.",
                                "mypack:mining",
                                CoreDiagnostics.TREE_REFUND_DENIED, 60).build()
                )
        );
    }

    private static SchemaDescriptor classIntent() {
        return schema(
                "class_intent",
                SchemaAudience.INTERNAL,
                "Class mutation intent payload",
                "Bounded serverbound class selection with no client supplied costs, grants, capacity, or outcomes.",
                List.of(
                        runtimeField("class_id", SchemaValueType.RESOURCE_LOCATION,
                                "Selected class or class removed by a swap.", "mypack:mage",
                                CoreDiagnostics.INVALID_CLASS, 10).build(),
                        runtimeOptionalField("preview_digest", SchemaValueType.STRING,
                                "Required only by respec and swap confirmations.", "c".repeat(64),
                                CoreDiagnostics.CLASS_RESPEC_DENIED, 30).build(),
                        runtimeOptionalField("replacement_class_id", SchemaValueType.RESOURCE_LOCATION,
                                "Required only by swap preview and confirmation.", "mypack:warrior",
                                CoreDiagnostics.INVALID_CLASS, 20).build()
                )
        );
    }

    private static SchemaDescriptor classChangePreview() {
        return schema(
                "class_change_preview",
                SchemaAudience.INTERNAL,
                "Class respec or swap preview payload",
                "Clientbound revision pinned affected classes, authoritative currency costs, blockers, and confirmation digest.",
                List.of(
                        networkField("affected_classes", SchemaValueType.LIST,
                                "Bounded stable set of classes whose selected or active state changes.",
                                "[\"mypack:mage\", \"mypack:warrior\"]",
                                CoreDiagnostics.CLASS_RESPEC_DENIED, 90).build(),
                        networkField("blockers", SchemaValueType.LIST,
                                "Bounded reasons that prevent confirmation.", "[]",
                                CoreDiagnostics.CLASS_RESPEC_DENIED, 120).build(),
                        networkField("class_id", SchemaValueType.RESOURCE_LOCATION,
                                "Selected class to remove.", "mypack:mage",
                                CoreDiagnostics.INVALID_CLASS, 70).build(),
                        networkField("cost_balances", SchemaValueType.MAP,
                                "Authoritative nonnegative named currency totals charged by the change.",
                                "{ \"progressiveskills:global_points\" = 2 }",
                                CoreDiagnostics.CLASS_RESPEC_DENIED, 100).build(),
                        networkField("definition_generation", SchemaValueType.INTEGER,
                                "Definition generation used to calculate the preview.", "11",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 30).build(),
                        networkField("intent_type", SchemaValueType.ENUM,
                                "Class respec or swap preview family.", "class_swap_preview",
                                CoreDiagnostics.NETWORK_PROTOCOL_MISMATCH, 60)
                                .allowedValues("class_respec_preview", "class_swap_preview").build(),
                        networkField("preview_digest", SchemaValueType.STRING,
                                "Lowercase SHA 256 covering the complete authoritative change.",
                                "d".repeat(64), CoreDiagnostics.CLASS_RESPEC_DENIED, 110).build(),
                        networkOptionalField("replacement_class_id", SchemaValueType.RESOURCE_LOCATION,
                                "Replacement class present only for a swap.", "mypack:warrior",
                                CoreDiagnostics.INVALID_CLASS, 80).build(),
                        networkField("request_id", SchemaValueType.INTEGER,
                                "Preview request identity returned to the requesting client.", "14",
                                CoreDiagnostics.NETWORK_RATE_LIMITED, 20).build(),
                        networkField("semantic_digest", SchemaValueType.STRING,
                                "Gameplay definition digest used by the preview.", "e".repeat(64),
                                CoreDiagnostics.NETWORK_STALE_REVISION, 40).build(),
                        networkField("session_id", SchemaValueType.STRING,
                                "Current connection session UUID.",
                                "00000000-0000-0000-0000-000000000711",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 10).build(),
                        networkField("state_revision", SchemaValueType.INTEGER,
                                "Authoritative state revision used by the preview.", "8",
                                CoreDiagnostics.NETWORK_STALE_REVISION, 50).build()
                )
        );
    }

    private static SchemaDescriptor visibleClassSelection() {
        return schema(
                "visible_class_selection",
                SchemaAudience.INTERNAL,
                "Visible selected class state",
                "Owner visible class identity, weighted slot use, and active or suspended status without raw source ownership.",
                List.of(
                        networkField("activity", SchemaValueType.ENUM,
                                "Explicit noncolor active or suspended status.", "active",
                                CoreDiagnostics.CLASS_RECONCILIATION_REQUIRED, 30)
                                .allowedValues("active", "suspended").build(),
                        networkField("class_id", SchemaValueType.RESOURCE_LOCATION,
                                "Selected stable class identity.", "mypack:mage",
                                CoreDiagnostics.INVALID_CLASS, 10).build(),
                        networkField("slot_cost", SchemaValueType.INTEGER,
                                "Visible weighted slot use including zero cost background classes.", "1",
                                CoreDiagnostics.INVALID_CLASS, 40).build(),
                        networkOptionalField("slot_id", SchemaValueType.RESOURCE_LOCATION,
                                "Slot occupied by a known selected class. Missing definitions remain visibly suspended.", "mypack:combat",
                                CoreDiagnostics.INVALID_CLASS_SLOT, 20).build()
                )
        );
    }

    private static SchemaDescriptor abilityIntent() {
        return schema(
                "ability_intent",
                SchemaAudience.INTERNAL,
                "Ability mutation intent payload",
                "Bounded serverbound assignment, selection, toggle, or activation identity without client supplied outcomes.",
                List.of(
                        runtimeOptionalField("ability_id", SchemaValueType.RESOURCE_LOCATION,
                                "Ability required by assign and toggle and forbidden by slot only intents.",
                                "mypack:second_wind", CoreDiagnostics.ABILITY_INTENT_DENIED, 10).build(),
                        runtimeOptionalField("slot", SchemaValueType.INTEGER,
                                "Fixed slot from zero through seven required by assign, unassign, select, and activate.",
                                "0", CoreDiagnostics.ABILITY_INTENT_DENIED, 20).build()
                )
        );
    }

    private static SchemaDescriptor visibleAbilityState() {
        return schema(
                "visible_ability_state",
                SchemaAudience.INTERNAL,
                "Visible owned ability state",
                "Owner visible toggle, charge, and cooldown status without raw source ownership or internal balances.",
                List.of(
                        networkField("ability_id", SchemaValueType.RESOURCE_LOCATION,
                                "Owned stable ability identity.", "mypack:second_wind",
                                CoreDiagnostics.INVALID_ABILITY, 10).build(),
                        networkField("charges", SchemaValueType.INTEGER,
                                "Currently available bounded charges.", "1",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 30).build(),
                        networkField("cooldown_remaining_ticks", SchemaValueType.INTEGER,
                                "Nonnegative shared cooldown ticks remaining.", "80",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 50).build(),
                        networkField("maximum_charges", SchemaValueType.INTEGER,
                                "Definition bounded maximum charges from one through sixteen.", "2",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 40).build(),
                        networkField("toggled_on", SchemaValueType.BOOLEAN,
                                "Current effective toggle state. Passive and active abilities report false.", "false",
                                CoreDiagnostics.NETWORK_RESYNC_REQUIRED, 20).build()
                )
        );
    }

    private static SchemaDescriptor visibleAbilitySlot() {
        return schema(
                "visible_ability_slot",
                SchemaAudience.INTERNAL,
                "Visible fixed ability slot",
                "One owner visible fixed slot assignment using startup registered input mappings.",
                List.of(
                        networkField("ability_id", SchemaValueType.RESOURCE_LOCATION,
                                "Owned slottable ability assigned to this position.", "mypack:second_wind",
                                CoreDiagnostics.INVALID_ABILITY, 20).build(),
                        networkField("slot", SchemaValueType.INTEGER,
                                "Fixed slot number from zero through seven.", "0",
                                CoreDiagnostics.ABILITY_INTENT_DENIED, 10).build()
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

    private static FieldDescriptor.Builder networkOptionalField(
            String path,
            SchemaValueType type,
            String description,
            String example,
            DiagnosticCode diagnostic,
            int order
    ) {
        return field(path, type, false, description, example, diagnostic, EditorWidget.OBJECT, order)
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
