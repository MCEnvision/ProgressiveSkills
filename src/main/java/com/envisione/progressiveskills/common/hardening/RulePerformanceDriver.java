package com.envisione.progressiveskills.common.hardening;

import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.requirement.CompiledRequirement;
import com.envisione.progressiveskills.common.requirement.RequirementContext;
import com.envisione.progressiveskills.common.requirement.RequirementExpression;
import com.envisione.progressiveskills.common.rule.RuleAntiExploit;
import com.envisione.progressiveskills.common.rule.RuleDefinition;
import com.envisione.progressiveskills.common.rule.RuleMatcherSpec;
import com.envisione.progressiveskills.common.rule.RuleStackResolver;
import com.envisione.progressiveskills.common.rule.RuleStackRule;
import com.envisione.progressiveskills.common.rule.RuleTriggerRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RulePerformanceDriver {
    private static final RequirementContext EMPTY_CONTEXT = ignored -> RequirementContext.Lookup.missing();
    private final Map<ResourceLocation, List<Route>> routes;
    private final int definitions;
    private final int routeCount;

    private RulePerformanceDriver(
            Map<ResourceLocation, List<Route>> routes,
            int definitions,
            int routeCount
    ) {
        this.routes = routes;
        this.definitions = definitions;
        this.routeCount = routeCount;
    }

    static RulePerformanceDriver compile(PerformanceWorkload.Contract contract) {
        var index = new HashMap<ResourceLocation, List<Route>>();
        ResourceLocation skill = id("skill");
        for (int definition = 0; definition < contract.definitionCount(); definition++) {
            ResourceLocation ruleId = id("rule_" + definition);
            var matchers = new ArrayList<RuleMatcherSpec>(contract.routesPerDefinition());
            for (int route = 0; route < contract.routesPerDefinition(); route++) {
                ResourceLocation selector = route == 1
                        ? id("cluster_" + definition / 4)
                        : id("selector_" + definition + "_" + route);
                matchers.add(new RuleMatcherSpec("id", selector.toString(), false));
            }
            RuleDefinition rule = new RuleDefinition(
                    ruleId, true, RuleTriggerRegistry.BLOCK_BREAK, definition % 17,
                    ruleId, RuleStackRule.SUM, "actor", matchers, false,
                    RequirementExpression.always(), 1L, ExpressionRounding.FLOOR,
                    List.of(), RuleAntiExploit.defaults(),
                    new RuleDefinition.XpOutput(id("output_" + definition), skill));
            Route compiled = new Route(rule, CompiledRequirement.compile(rule.requirements()));
            for (RuleMatcherSpec matcher : rule.matchers()) {
                index.computeIfAbsent(matcher.idValue(), ignored -> new ArrayList<>()).add(compiled);
            }
        }
        var immutable = new LinkedHashMap<ResourceLocation, List<Route>>();
        index.entrySet().stream().sorted(Map.Entry.comparingByKey(ResourceLocation::compareNamespaced))
                .forEach(entry -> immutable.put(entry.getKey(), List.copyOf(entry.getValue())));
        int routeCount = immutable.values().stream().mapToInt(List::size).sum();
        if (routeCount != contract.routedRuleCount()) {
            throw new IllegalStateException("Compiled performance route count changed");
        }
        return new RulePerformanceDriver(
                Collections.unmodifiableMap(immutable), contract.definitionCount(), routeCount);
    }

    List<RuleStackResolver.Candidate> resolve(int desiredMatches, long eventOrdinal) {
        if (desiredMatches != 0 && desiredMatches != 1 && desiredMatches != 4) {
            throw new IllegalArgumentException("Performance route match count is invalid");
        }
        if (desiredMatches == 0) {
            return List.of();
        }
        int definition = Math.floorMod(eventOrdinal, definitions);
        ResourceLocation selector = desiredMatches == 4
                ? id("cluster_" + definition / 4)
                : id("selector_" + definition + "_0");
        List<Route> matched = routes.getOrDefault(selector, List.of());
        var candidates = new ArrayList<RuleStackResolver.Candidate>(matched.size());
        for (Route route : matched) {
            if (route.requirements().evaluate(EMPTY_CONTEXT, false).passed()) {
                candidates.add(new RuleStackResolver.Candidate(
                        route.rule(), route.rule().multipliedBaseUnits()));
            }
        }
        List<RuleStackResolver.Candidate> resolved = RuleStackResolver.resolve(candidates);
        if (resolved.size() != desiredMatches) {
            throw new IllegalStateException("Performance rule route selection changed");
        }
        return resolved;
    }

    int routeCount() {
        return routeCount;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("perf", path);
    }

    private record Route(RuleDefinition rule, CompiledRequirement requirements) {
    }
}
