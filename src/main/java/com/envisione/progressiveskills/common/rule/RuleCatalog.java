package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.requirement.CompiledRequirement;
import com.envisione.progressiveskills.common.requirement.RequirementDependency;
import com.envisione.progressiveskills.common.requirement.RequirementDependencyIndex;
import com.envisione.progressiveskills.common.requirement.RequirementExpression;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class RuleCatalog {
    public static final int MAX_RULES = 10_000;
    public static final int MAX_REQUIREMENT_EDGES = 320_000;
    private final Map<ResourceLocation, RuleDefinition> rules;
    private final RequirementDependencyIndex<ResourceLocation> requirementIndex;

    private RuleCatalog(
            Map<ResourceLocation, RuleDefinition> rules,
            RequirementDependencyIndex<ResourceLocation> requirementIndex
    ) {
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        this.requirementIndex = Objects.requireNonNull(requirementIndex, "requirementIndex");
    }

    public static RuleCatalog from(CanonicalIr ir, SkillCatalog skills) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(skills, "skills");
        RuleTriggerRegistry triggers = RuleTriggerRegistry.core();
        RuleMatcherRegistry matchers = RuleMatcherRegistry.core();
        RuleProviderRegistry providers = RuleProviderRegistry.core();
        var rules = new TreeMap<ResourceLocation, RuleDefinition>(ResourceLocation::compareNamespaced);
        ir.definitions().forEach((key, canonical) -> {
            if (!key.kind().equals(DefinitionKinds.RULE)) {
                return;
            }
            RuleDefinition rule = RuleCanonicalCodec.decode(canonical);
            RuleTriggerRegistry.Trigger trigger = triggers.require(rule.trigger());
            providers.requireProvider(rule.trigger());
            for (RuleMatcherSpec matcher : rule.matchers()) {
                if (!matchers.require(matcher.prefix()).supports(trigger.subjectType())) {
                    throw new IllegalArgumentException("Matcher " + matcher.prefix()
                            + " does not support trigger " + rule.trigger());
                }
            }
            if (skills.skill(rule.output().skill()).isEmpty()) {
                throw new IllegalArgumentException("Rule " + rule.id()
                        + " references missing skill " + rule.output().skill());
            }
            for (RequirementDependency dependency : CompiledRequirement.compile(rule.requirements()).dependencies()) {
                if (dependency.kind() == RequirementDependency.Kind.SKILL_LEVEL
                        && skills.skill(dependency.id()).isEmpty()) {
                    throw new IllegalArgumentException("Rule " + rule.id()
                            + " requirement references missing skill " + dependency.id());
                }
                if (dependency.kind() == RequirementDependency.Kind.CURRENCY
                        && skills.currency(dependency.id()).isEmpty()) {
                    throw new IllegalArgumentException("Rule " + rule.id()
                            + " requirement references missing currency " + dependency.id());
                }
            }
            if (rules.putIfAbsent(rule.id(), rule) != null) {
                throw new IllegalArgumentException("Duplicate rule id " + rule.id());
            }
        });
        if (rules.size() > MAX_RULES) {
            throw new IllegalArgumentException("Rule catalog exceeds " + MAX_RULES + " definitions");
        }
        var stackPolicies = new TreeMap<ResourceLocation, RuleStackRule>(ResourceLocation::compareNamespaced);
        for (RuleDefinition rule : rules.values()) {
            RuleStackRule previous = stackPolicies.putIfAbsent(rule.stackGroup(), rule.stackRule());
            if (previous != null && previous != rule.stackRule()) {
                throw new IllegalArgumentException("Rule stack group " + rule.stackGroup()
                        + " uses conflicting policies");
            }
        }
        var requirements = new LinkedHashMap<ResourceLocation, RequirementExpression>();
        rules.forEach((id, rule) -> requirements.put(id, rule.requirements()));
        return new RuleCatalog(rules, RequirementDependencyIndex.compile(
                requirements,
                ResourceLocation::compareNamespaced,
                MAX_REQUIREMENT_EDGES
        ));
    }

    public Map<ResourceLocation, RuleDefinition> rules() {
        return rules;
    }

    public Optional<RuleDefinition> rule(ResourceLocation id) {
        return Optional.ofNullable(rules.get(id));
    }

    public RequirementDependencyIndex<ResourceLocation> requirementIndex() {
        return requirementIndex;
    }
}
