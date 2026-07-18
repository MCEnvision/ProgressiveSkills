package com.envisione.progressiveskills.common.creator;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import com.envisione.progressiveskills.common.skill.SkillCatalog;

public final class CreatorCatalog {
    private static final Set<DefinitionKind> ADVANCED = Set.of(
            DefinitionKinds.ANTI_EXPLOIT_PROFILE,
            DefinitionKinds.CATEGORY,
            DefinitionKinds.CHALLENGE,
            DefinitionKinds.CLASS_RANK,
            DefinitionKinds.CONTEXT_EFFECT,
            DefinitionKinds.CONVERSION,
            DefinitionKinds.COST_BUNDLE,
            DefinitionKinds.CURVE,
            DefinitionKinds.GLOBAL_LEVEL,
            DefinitionKinds.GRANT_BUNDLE,
            DefinitionKinds.ITEM_STACK_SPEC,
            DefinitionKinds.LAYOUT,
            DefinitionKinds.LOADOUT,
            DefinitionKinds.MILESTONE_CHOICE,
            DefinitionKinds.NOTIFICATION_PROFILE,
            DefinitionKinds.PREDICATE,
            DefinitionKinds.PRESTIGE,
            DefinitionKinds.PRIMITIVE,
            DefinitionKinds.PROFILE,
            DefinitionKinds.REQUIREMENT,
            DefinitionKinds.REACTIVE_PROC,
            DefinitionKinds.RESOURCE,
            DefinitionKinds.SEASON,
            DefinitionKinds.SIMULATION,
            DefinitionKinds.STATION,
            DefinitionKinds.STANCE,
            DefinitionKinds.TARGETING_PROFILE,
            DefinitionKinds.TEMPLATE,
            DefinitionKinds.THEME,
            DefinitionKinds.TRAINING_CONTRACT,
            DefinitionKinds.TREE_RANK,
            DefinitionKinds.COMBO_MASTERY,
            DefinitionKinds.VARIABLE
    );
    private final Map<DefinitionKey, CreatorDefinition> definitions;

    private CreatorCatalog(Map<DefinitionKey, CreatorDefinition> definitions) {
        this.definitions = definitions;
    }

    public static CreatorCatalog from(CanonicalIr ir) {
        Objects.requireNonNull(ir, "ir");
        var result = new TreeMap<DefinitionKey, CreatorDefinition>();
        ir.definitions().forEach((key, definition) -> {
            if (ADVANCED.contains(key.kind())) {
                result.put(key, new CreatorDefinition(key, definition.fields()));
            }
        });
        var catalog = new CreatorCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(result)));
        catalog.validate(ir);
        return catalog;
    }

    public Map<DefinitionKey, CreatorDefinition> definitions() {
        return definitions;
    }

    public List<CreatorDefinition> kind(DefinitionKind kind) {
        return definitions.entrySet().stream().filter(entry -> entry.getKey().kind().equals(kind))
                .map(Map.Entry::getValue).toList();
    }

    public java.util.Optional<CreatorDefinition> definition(DefinitionKind kind, ResourceLocation id) {
        return java.util.Optional.ofNullable(definitions.get(new DefinitionKey(kind, id)));
    }

    public CreatorDefinition resolvedTemplate(ResourceLocation id) {
        CreatorDefinition template = definition(DefinitionKinds.TEMPLATE, id).orElseThrow(
                () -> new IllegalArgumentException("Unknown template " + id));
        var fields = new LinkedHashMap<String, com.envisione.progressiveskills.common.ir.CanonicalValue>();
        resolveTemplateFields(template, fields, new HashSet<>());
        return new CreatorDefinition(template.key(),
                new com.envisione.progressiveskills.common.ir.CanonicalValue.ObjectValue(fields));
    }

    private void validate(CanonicalIr ir) {
        SkillCatalog skills = SkillCatalog.from(ir);
        validateTemplates();
        validatePredicates();
        definitions.values().forEach(value -> value.text("formula").ifPresent(formula ->
                CreatorFormulaParser.compile(formula,
                        com.envisione.progressiveskills.common.expression.ExpressionRounding.FLOOR)));
        kind(DefinitionKinds.VARIABLE).forEach(value -> {
            if (value.decimal("value").isEmpty()) {
                throw new IllegalArgumentException(value.key() + " requires numeric value");
            }
        });
        kind(DefinitionKinds.RESOURCE).forEach(value -> {
            long minimum = value.integer("minimum").orElse(0L);
            long maximum = value.integer("maximum").orElseThrow(
                    () -> new IllegalArgumentException(value.key() + " requires maximum"));
            long initial = value.integer("initial").orElse(minimum);
            long decay = value.integer("decay_amount").orElse(0L);
            long interval = value.integer("decay_interval_ticks").orElse(0L);
            if (minimum > initial || initial > maximum || decay < 0 || interval < 0
                    || decay == 0 != (interval == 0)) {
                throw new IllegalArgumentException(value.key() + " resource bounds are invalid");
            }
        });
        kind(DefinitionKinds.CONVERSION).forEach(value -> {
            ResourceLocation from = value.id("from").orElseThrow(
                    () -> new IllegalArgumentException(value.key() + " conversion source is unavailable"));
            ResourceLocation to = value.id("to").orElseThrow(
                    () -> new IllegalArgumentException(value.key() + " conversion target is unavailable"));
            long fee = value.integer("fee_basis_points").orElse(0L);
            long maximumInput = value.integer("maximum_input").orElse(Long.MAX_VALUE);
            if (from.equals(to) || skills.currency(from).isEmpty() || skills.currency(to).isEmpty()
                    || value.integer("numerator").orElse(0L) < 1
                    || value.integer("denominator").orElse(0L) < 1
                    || fee < 0 || fee > 10_000 || maximumInput < 1) {
                throw new IllegalArgumentException(value.key() + " conversion is invalid");
            }
        });
        kind(DefinitionKinds.MILESTONE_CHOICE).forEach(value -> {
            int size = value.list("choices").size();
            if (size < 2 || size > 32) {
                throw new IllegalArgumentException(value.key() + " choice count is invalid");
            }
            var ids = new HashSet<ResourceLocation>();
            value.list("choices").forEach(choice -> {
                Map<String, CreatorDefinition.CreatorValue> fields = choice.object();
                ResourceLocation id = java.util.Optional.ofNullable(fields.get("id"))
                        .flatMap(CreatorDefinition.CreatorValue::text)
                        .map(com.envisione.progressiveskills.common.id.StableId::parse)
                        .orElseThrow(() -> new IllegalArgumentException(
                                value.key() + " milestone choice id is unavailable"));
                ResourceLocation reward = java.util.Optional.ofNullable(fields.get("reward_bundle"))
                        .flatMap(CreatorDefinition.CreatorValue::text)
                        .map(com.envisione.progressiveskills.common.id.StableId::parse)
                        .orElseThrow(() -> new IllegalArgumentException(
                                value.key() + " milestone reward bundle is unavailable"));
                if (!ids.add(id) || definition(DefinitionKinds.GRANT_BUNDLE, reward).isEmpty()) {
                    throw new IllegalArgumentException(value.key() + " milestone choice is invalid");
                }
            });
            String respecPolicy = value.text("respec_policy").orElse("replace_without_reward");
            if (!respecPolicy.equals("replace_without_reward") && !respecPolicy.equals("grant_each_choice")) {
                throw new IllegalArgumentException(value.key() + " milestone respec policy is invalid");
            }
        });
        kind(DefinitionKinds.GRANT_BUNDLE).forEach(value -> {
            List<CreatorDefinition.CreatorValue> grants = value.list("grants");
            if (grants.isEmpty() || grants.size() > 32) {
                throw new IllegalArgumentException(value.key() + " grant bundle size is invalid");
            }
            grants.forEach(grant -> {
                Map<String, CreatorDefinition.CreatorValue> fields = grant.object();
                boolean currency = java.util.Optional.ofNullable(fields.get("currency"))
                        .flatMap(CreatorDefinition.CreatorValue::text).isPresent();
                ResourceLocation currencyId = java.util.Optional.ofNullable(fields.get("currency"))
                        .flatMap(CreatorDefinition.CreatorValue::text)
                        .map(com.envisione.progressiveskills.common.id.StableId::parse).orElse(null);
                long amount = java.util.Optional.ofNullable(fields.get("amount"))
                        .filter(entry -> entry.integer().isPresent())
                        .map(entry -> entry.integer().orElseThrow()).orElse(0L);
                if (!currency || currencyId == null || skills.currency(currencyId).isEmpty() || amount < 1) {
                    throw new IllegalArgumentException(value.key() + " grant bundle entry is invalid");
                }
            });
        });
        kind(DefinitionKinds.TRAINING_CONTRACT).forEach(value -> {
            long minimum = value.integer("minimum_goal").orElse(1L);
            long maximum = value.integer("maximum_goal").orElse(minimum);
            if (minimum < 1 || maximum < minimum) {
                throw new IllegalArgumentException(value.key() + " training goal bounds are invalid");
            }
            if (value.ids("eligible_skills").stream().anyMatch(id -> skills.skill(id).isEmpty())) {
                throw new IllegalArgumentException(value.key() + " training skill is unavailable");
            }
        });
        kind(DefinitionKinds.COMBO_MASTERY).forEach(value -> {
            int length = value.texts("sequence").size();
            long timeout = value.integer("timeout_ticks").orElse(200L);
            long cooldown = value.integer("cooldown_ticks").orElse(20L);
            long cap = value.integer("mastery_cap_per_window").orElse(10L);
            long window = value.integer("cap_window_ticks").orElse(1200L);
            long repeats = value.integer("max_repeated_awards").orElse(4L);
            if (length < 2 || length > 32 || timeout < 1 || cooldown < 0
                    || cap < 1 || cap > 100000 || window < 1 || repeats < 1 || repeats > 32) {
                throw new IllegalArgumentException(value.key() + " combo sequence is invalid");
            }
        });
        kind(DefinitionKinds.REACTIVE_PROC).forEach(value -> {
            long chance = value.integer("chance_basis_points").orElse(10_000L);
            long cooldown = value.integer("cooldown_ticks").orElse(0L);
            if (value.id("trigger").isEmpty() || chance < 0 || chance > 10_000 || cooldown < 0) {
                throw new IllegalArgumentException(value.key() + " reactive proc is invalid");
            }
            requireResource(value, "resource");
        });
        kind(DefinitionKinds.CONTEXT_EFFECT).forEach(value -> {
            long cooldown = value.integer("cooldown_ticks").orElse(20L);
            ResourceLocation predicate = value.id("predicate").orElseThrow(
                    () -> new IllegalArgumentException(value.key() + " context predicate is unavailable"));
            if (definition(DefinitionKinds.PREDICATE, predicate).isEmpty() || cooldown < 0) {
                throw new IllegalArgumentException(value.key() + " context effect is invalid");
            }
            requireResource(value, "resource");
        });
        kind(DefinitionKinds.PRESTIGE).forEach(value -> {
            if (value.integer("minimum_total_level").orElse(0L) < 0
                    || value.integer("conversion_per_level").orElse(0L) < 0
                    || value.integer("reward_amount").orElse(1L) < 0
                    || value.integer("reward_currency_amount").orElse(1L) < 1) {
                throw new IllegalArgumentException(value.key() + " prestige values are invalid");
            }
            value.ids("reset_currencies").forEach(id -> requireCurrency(skills, value, id));
            value.id("conversion_currency").ifPresent(id -> requireCurrency(skills, value, id));
            value.id("reward_currency").ifPresent(id -> requireCurrency(skills, value, id));
            requireResource(value, "reward_resource");
        });
        for (var kind : List.of(DefinitionKinds.TREE_RANK, DefinitionKinds.CLASS_RANK)) {
            kind(kind).forEach(value -> {
                long maximum = value.integer("maximum_rank").orElse(1L);
                long base = value.integer("base_cost").orElse(1L);
                long growth = value.integer("cost_growth").orElse(0L);
                ResourceLocation currency = value.id("cost_currency").orElseThrow(
                        () -> new IllegalArgumentException(value.key() + " rank currency is unavailable"));
                if (maximum < 1 || maximum > Integer.MAX_VALUE || base < 0 || growth < 0) {
                    throw new IllegalArgumentException(value.key() + " rank values are invalid");
                }
                requireCurrency(skills, value, currency);
            });
        }
        kind(DefinitionKinds.STANCE).forEach(value -> {
            if (value.id("group").isEmpty()) {
                throw new IllegalArgumentException(value.key() + " stance group is unavailable");
            }
        });
        kind(DefinitionKinds.CHALLENGE).forEach(value -> {
            long progress = value.integer("progress_per_xp").orElse(1L);
            if (value.integer("goal").orElse(0L) < 1 || progress < 0
                    || value.id("skill").filter(id -> skills.skill(id).isEmpty()).isPresent()) {
                throw new IllegalArgumentException(value.key() + " challenge goal is invalid");
            }
        });
        kind(DefinitionKinds.PROFILE).forEach(value -> {
            long basisPoints = value.integer("mentor_bonus_basis_points").orElse(500L);
            long maximumBonus = value.integer("mentor_max_bonus_per_award").orElse(1_000L);
            long maximumGap = value.integer("mentor_max_level_gap").orElse(20L);
            long xpPerDamage = value.integer("assist_xp_units_per_damage").orElse(0L);
            long maximumAward = value.integer("assist_max_award_units").orElse(10_000L);
            long window = value.integer("assist_window_ticks").orElse(200L);
            long minimumDamage = value.integer("assist_min_damage_milli").orElse(1_000L);
            long cooldown = value.integer("pvp_pair_cooldown_ticks").orElse(1_200L);
            long dailyCap = value.integer("pvp_pair_daily_cap_units").orElse(25_000L);
            long repeatMultiplier = value.integer("pvp_repeat_multiplier_basis_points").orElse(2_500L);
            long freeGap = value.integer("pvp_free_level_gap").orElse(5L);
            long gapPenalty = value.integer("pvp_level_penalty_basis_points").orElse(500L);
            long minimumMultiplier = value.integer("pvp_minimum_multiplier_basis_points").orElse(1_000L);
            if (basisPoints < 0 || basisPoints > 10_000 || maximumBonus < 0 || maximumGap < 0
                    || xpPerDamage < 0 || maximumAward < 0 || window < 1 || window > 12_000
                    || minimumDamage < 1 || minimumDamage > 1_000_000_000L
                    || cooldown < 0 || cooldown > 1_728_000 || dailyCap < 0
                    || repeatMultiplier < 0 || repeatMultiplier > 10_000
                    || freeGap < 0 || freeGap > 1_000_000
                    || gapPenalty < 0 || gapPenalty > 10_000
                    || minimumMultiplier < 0 || minimumMultiplier > 10_000) {
                throw new IllegalArgumentException(value.key() + " profile mentor values are invalid");
            }
            value.id("assist_skill").ifPresent(id -> requireSkill(skills, value, id));
        });
    }

    private void requireResource(CreatorDefinition owner, String field) {
        owner.id(field).ifPresent(id -> {
            if (definition(DefinitionKinds.RESOURCE, id).isEmpty()) {
                throw new IllegalArgumentException(owner.key() + " resource is unavailable " + id);
            }
        });
    }

    private static void requireCurrency(
            SkillCatalog skills,
            CreatorDefinition owner,
            ResourceLocation id
    ) {
        if (skills.currency(id).isEmpty()) {
            throw new IllegalArgumentException(owner.key() + " currency is unavailable " + id);
        }
    }

    private static void requireSkill(
            SkillCatalog skills,
            CreatorDefinition owner,
            ResourceLocation id
    ) {
        if (skills.skill(id).isEmpty()) {
            throw new IllegalArgumentException(owner.key() + " skill is unavailable " + id);
        }
    }

    private void validatePredicates() {
        var complete = new HashSet<ResourceLocation>();
        for (CreatorDefinition predicate : kind(DefinitionKinds.PREDICATE)) {
            validatePredicate(predicate.key().id(), new HashSet<>(), complete);
        }
    }

    private void validatePredicate(
            ResourceLocation id,
            Set<ResourceLocation> visiting,
            Set<ResourceLocation> complete
    ) {
        if (complete.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Predicate graph contains a cycle at " + id);
        }
        CreatorDefinition value = definition(DefinitionKinds.PREDICATE, id).orElseThrow(
                () -> new IllegalArgumentException("Predicate is unavailable " + id));
        String type = value.text("type").orElse("all");
        switch (type) {
            case "all", "any" -> {
                List<ResourceLocation> children = value.ids("predicates");
                if (children.isEmpty()) {
                    throw new IllegalArgumentException(value.key() + " predicate children are unavailable");
                }
                children.forEach(child -> validatePredicate(child, visiting, complete));
            }
            case "not" -> validatePredicate(value.id("predicate").orElseThrow(
                    () -> new IllegalArgumentException(value.key() + " predicate child is unavailable")),
                    visiting, complete);
            case "flag" -> value.id("flag").orElseThrow(
                    () -> new IllegalArgumentException(value.key() + " predicate flag is unavailable"));
            case "value" -> {
                value.id("value").orElseThrow(
                        () -> new IllegalArgumentException(value.key() + " predicate value is unavailable"));
                if (!Set.of("equal", "not_equal", "less", "less_or_equal", "greater", "greater_or_equal")
                        .contains(value.text("operator").orElse("greater_or_equal"))) {
                    throw new IllegalArgumentException(value.key() + " predicate operator is invalid");
                }
            }
            default -> throw new IllegalArgumentException(value.key() + " predicate type is invalid");
        }
        visiting.remove(id);
        complete.add(id);
    }

    private void validateTemplates() {
        Map<ResourceLocation, CreatorDefinition> templates = new TreeMap<>(ResourceLocation::compareNamespaced);
        kind(DefinitionKinds.TEMPLATE).forEach(value -> templates.put(value.key().id(), value));
        var visiting = new HashSet<ResourceLocation>();
        var complete = new HashSet<ResourceLocation>();
        for (ResourceLocation template : templates.keySet()) {
            visitTemplate(template, templates, visiting, complete);
        }
    }

    private void resolveTemplateFields(
            CreatorDefinition template,
            Map<String, com.envisione.progressiveskills.common.ir.CanonicalValue> fields,
            Set<ResourceLocation> visiting
    ) {
        if (!visiting.add(template.key().id())) {
            throw new IllegalArgumentException("Template inheritance contains a cycle at " + template.key().id());
        }
        for (ResourceLocation parent : template.ids("extends")) {
            CreatorDefinition parentDefinition = definition(DefinitionKinds.TEMPLATE, parent).orElseThrow(
                    () -> new IllegalArgumentException("Template is unavailable " + parent));
            resolveTemplateFields(parentDefinition, fields, visiting);
        }
        com.envisione.progressiveskills.common.ir.CanonicalValue value =
                template.fields().fields().get("fields");
        if (!(value instanceof com.envisione.progressiveskills.common.ir.CanonicalValue.ObjectValue object)) {
            throw new IllegalArgumentException("Template fields are unavailable " + template.key().id());
        }
        fields.putAll(object.fields());
        visiting.remove(template.key().id());
    }

    private static void visitTemplate(
            ResourceLocation id,
            Map<ResourceLocation, CreatorDefinition> templates,
            Set<ResourceLocation> visiting,
            Set<ResourceLocation> complete
    ) {
        if (complete.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Template inheritance contains a cycle at " + id);
        }
        CreatorDefinition definition = templates.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Template is unavailable " + id);
        }
        String targetKind = definition.text("target_kind").orElseThrow(
                () -> new IllegalArgumentException("Template target kind is unavailable " + id));
        ResourceLocation targetKindId = targetKind.indexOf(':') >= 0
                ? com.envisione.progressiveskills.common.id.StableId.parse(targetKind)
                : ResourceLocation.fromNamespaceAndPath("progressiveskills", targetKind);
        com.envisione.progressiveskills.common.id.DefinitionKinds.require(targetKindId);
        if (!(definition.fields().fields().get("fields")
                instanceof com.envisione.progressiveskills.common.ir.CanonicalValue.ObjectValue)) {
            throw new IllegalArgumentException("Template fields are unavailable " + id);
        }
        for (ResourceLocation parent : definition.ids("extends")) {
            visitTemplate(parent, templates, visiting, complete);
            CreatorDefinition parentDefinition = templates.get(parent);
            String parentTarget = parentDefinition.text("target_kind").orElseThrow();
            ResourceLocation parentTargetId = parentTarget.indexOf(':') >= 0
                    ? com.envisione.progressiveskills.common.id.StableId.parse(parentTarget)
                    : ResourceLocation.fromNamespaceAndPath("progressiveskills", parentTarget);
            if (!targetKindId.equals(parentTargetId)) {
                throw new IllegalArgumentException("Template inheritance target kinds do not match at " + id);
            }
        }
        visiting.remove(id);
        complete.add(id);
    }
}
