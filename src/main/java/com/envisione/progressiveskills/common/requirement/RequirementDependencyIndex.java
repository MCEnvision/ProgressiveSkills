package com.envisione.progressiveskills.common.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class RequirementDependencyIndex<K> {
    private final Map<RequirementDependency, List<K>> consumers;
    private final int edgeCount;

    private RequirementDependencyIndex(Map<RequirementDependency, List<K>> consumers, int edgeCount) {
        this.consumers = consumers;
        this.edgeCount = edgeCount;
    }

    public static <K> RequirementDependencyIndex<K> compile(
            Map<K, RequirementExpression> expressions,
            Comparator<K> ownerOrder,
            int maxEdges
    ) {
        Objects.requireNonNull(expressions, "expressions");
        Objects.requireNonNull(ownerOrder, "ownerOrder");
        if (maxEdges < 0) {
            throw new IllegalArgumentException("Requirement edge limit must not be negative");
        }
        var mutable = new TreeMap<RequirementDependency, List<K>>();
        int edges = 0;
        for (Map.Entry<K, RequirementExpression> entry : expressions.entrySet()) {
            for (RequirementDependency dependency : CompiledRequirement.compile(entry.getValue()).dependencies()) {
                edges++;
                if (edges > maxEdges) {
                    throw new IllegalArgumentException("Requirement dependency edge budget exceeded");
                }
                mutable.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }
        var immutable = new LinkedHashMap<RequirementDependency, List<K>>();
        mutable.forEach((dependency, owners) -> {
            owners.sort(ownerOrder);
            immutable.put(dependency, List.copyOf(owners));
        });
        return new RequirementDependencyIndex<>(Collections.unmodifiableMap(immutable), edges);
    }

    public List<K> consumers(RequirementDependency dependency) {
        return consumers.getOrDefault(dependency, List.of());
    }

    public Map<RequirementDependency, List<K>> consumers() {
        return consumers;
    }

    public int edgeCount() {
        return edgeCount;
    }
}
