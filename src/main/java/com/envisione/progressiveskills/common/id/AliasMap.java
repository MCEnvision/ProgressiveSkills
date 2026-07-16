package com.envisione.progressiveskills.common.id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable, prevalidated aliases with deterministic and bounded terminal resolution. */
public final class AliasMap {
    public static final int MAX_ALIAS_DECLARATIONS = 10_000;
    public static final int MAX_RESOLUTION_HOPS = 64;
    private static final AliasMap EMPTY = new AliasMap(Map.of());

    private final NavigableMap<DefinitionKey, DefinitionKey> mappings;

    private AliasMap(Map<DefinitionKey, DefinitionKey> mappings) {
        this.mappings = Collections.unmodifiableNavigableMap(new TreeMap<>(mappings));
    }

    public static AliasMap empty() {
        return EMPTY;
    }

    public static AliasMap of(Collection<Alias> aliases) {
        return validate(aliases).orThrow();
    }

    public static AliasValidationResult validate(Collection<Alias> aliases) {
        Objects.requireNonNull(aliases, "aliases");
        if (aliases.size() > MAX_ALIAS_DECLARATIONS) {
            return AliasValidationResult.invalid(List.of(new AliasIssue(
                    AliasIssueCode.TOO_MANY_ALIASES,
                    "Alias declarations exceed the limit of " + MAX_ALIAS_DECLARATIONS + ": " + aliases.size(),
                    List.of()
            )));
        }
        List<Alias> orderedAliases = aliases.stream().map(alias -> Objects.requireNonNull(alias, "alias")).sorted().toList();
        Map<DefinitionKey, DefinitionKey> candidateMappings = new TreeMap<>();
        List<AliasIssue> issues = new ArrayList<>();

        for (Alias alias : orderedAliases) {
            DefinitionKey source = alias.source();
            DefinitionKey target = alias.target();
            if (!source.kind().equals(target.kind())) {
                issues.add(new AliasIssue(
                        AliasIssueCode.CROSS_KIND,
                        "Alias cannot cross definition kinds: " + source + " -> " + target,
                        List.of(source, target)
                ));
                continue;
            }
            if (source.equals(target)) {
                issues.add(new AliasIssue(
                        AliasIssueCode.SELF_ALIAS,
                        "Alias cannot target itself: " + source,
                        List.of(source)
                ));
                continue;
            }

            DefinitionKey previous = candidateMappings.putIfAbsent(source, target);
            if (previous != null && !previous.equals(target)) {
                List<DefinitionKey> related = new ArrayList<>(List.of(source, previous, target));
                related.sort(DefinitionKey::compareTo);
                issues.add(new AliasIssue(
                        AliasIssueCode.CONFLICTING_TARGET,
                        "Alias has conflicting targets: " + source + " -> " + previous + " and " + target,
                        related
                ));
            }
        }

        validateChains(candidateMappings, issues);
        validateNoChainedAliases(candidateMappings, issues);
        return issues.isEmpty()
                ? AliasValidationResult.valid(candidateMappings.isEmpty() ? EMPTY : new AliasMap(candidateMappings))
                : AliasValidationResult.invalid(issues);
    }

    private static void validateNoChainedAliases(
            Map<DefinitionKey, DefinitionKey> mappings,
            List<AliasIssue> issues
    ) {
        for (var entry : new TreeMap<>(mappings).entrySet()) {
            if (mappings.containsKey(entry.getValue())) {
                issues.add(new AliasIssue(
                        AliasIssueCode.CHAINED_ALIAS,
                        "Alias targets another retired alias; map the old id directly to the terminal id: "
                                + entry.getKey() + " -> " + entry.getValue(),
                        List.of(entry.getKey(), entry.getValue(), mappings.get(entry.getValue()))
                ));
            }
        }
    }

    private static void validateChains(Map<DefinitionKey, DefinitionKey> mappings, List<AliasIssue> issues) {
        Set<String> reportedCycles = new HashSet<>();
        Set<DefinitionKey> reportedLongChains = new HashSet<>();

        for (DefinitionKey start : new TreeMap<>(mappings).navigableKeySet()) {
            List<DefinitionKey> path = new ArrayList<>();
            Map<DefinitionKey, Integer> seenIndexes = new LinkedHashMap<>();
            DefinitionKey current = start;

            while (mappings.containsKey(current)) {
                Integer seenIndex = seenIndexes.putIfAbsent(current, path.size());
                if (seenIndex != null) {
                    List<DefinitionKey> cycle = new ArrayList<>(path.subList(seenIndex, path.size()));
                    List<DefinitionKey> signatureKeys = cycle.stream().sorted().toList();
                    String signature = signatureKeys.toString();
                    if (reportedCycles.add(signature)) {
                        cycle.add(current);
                        issues.add(new AliasIssue(
                                AliasIssueCode.CYCLE,
                                "Alias cycle detected: " + cycle,
                                cycle
                        ));
                    }
                    break;
                }

                path.add(current);
                if (path.size() > MAX_RESOLUTION_HOPS) {
                    if (reportedLongChains.add(start)) {
                        issues.add(new AliasIssue(
                                AliasIssueCode.CHAIN_TOO_LONG,
                                "Alias chain exceeds " + MAX_RESOLUTION_HOPS + " hops from " + start,
                                List.copyOf(path)
                        ));
                    }
                    break;
                }
                current = mappings.get(current);
            }
        }
    }

    public NavigableMap<DefinitionKey, DefinitionKey> mappings() {
        return mappings;
    }

    public boolean isEmpty() {
        return mappings.isEmpty();
    }

    public boolean containsSource(DefinitionKey key) {
        return mappings.containsKey(Objects.requireNonNull(key, "key"));
    }

    public DefinitionKey resolveTerminal(DefinitionKey key) {
        return resolve(key).terminal();
    }

    public AliasResolution resolve(DefinitionKey key) {
        Objects.requireNonNull(key, "key");
        List<DefinitionKey> path = new ArrayList<>();
        path.add(key);
        DefinitionKey current = key;
        for (int hops = 0; hops < MAX_RESOLUTION_HOPS; hops++) {
            DefinitionKey target = mappings.get(current);
            if (target == null) {
                return new AliasResolution(key, current, path);
            }
            path.add(target);
            current = target;
        }
        if (mappings.containsKey(current)) {
            throw new IllegalStateException("Prevalidated alias map exceeded its resolution bound from " + key);
        }
        return new AliasResolution(key, current, path);
    }

    public List<Alias> aliases() {
        return mappings.entrySet().stream().map(entry -> new Alias(entry.getKey(), entry.getValue())).toList();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AliasMap aliasMap && mappings.equals(aliasMap.mappings);
    }

    @Override
    public int hashCode() {
        return mappings.hashCode();
    }

    @Override
    public String toString() {
        return mappings.toString();
    }
}
