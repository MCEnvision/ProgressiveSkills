package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
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
    private final Map<ResourceLocation, RuleDefinition> rules;

    private RuleCatalog(Map<ResourceLocation, RuleDefinition> rules) {
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
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
        return new RuleCatalog(rules);
    }

    public Map<ResourceLocation, RuleDefinition> rules() {
        return rules;
    }

    public Optional<RuleDefinition> rule(ResourceLocation id) {
        return Optional.ofNullable(rules.get(id));
    }
}
