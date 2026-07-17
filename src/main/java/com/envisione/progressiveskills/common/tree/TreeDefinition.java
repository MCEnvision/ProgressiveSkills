package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record TreeDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        boolean enabled,
        TreeScope scope,
        Optional<ResourceLocation> boundSkill,
        ResourceLocation currency,
        TreeDependencyPolicy dependencyPolicy,
        List<TreeNodeDefinition> nodes
) {
    public static final int MAX_NODES = 64;
    public static final int MAX_EDGES = 4096;

    public TreeDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(boundSkill, "boundSkill");
        boundSkill = boundSkill.map(StableId::requireValid);
        currency = StableId.requireValid(currency);
        Objects.requireNonNull(dependencyPolicy, "dependencyPolicy");
        if (scope == TreeScope.SKILL && boundSkill.isEmpty()) {
            throw new IllegalArgumentException("A skill tree requires bind");
        }
        if (scope == TreeScope.GLOBAL && boundSkill.isPresent()) {
            throw new IllegalArgumentException("A global tree must not declare bind");
        }
        nodes = Objects.requireNonNull(nodes, "nodes").stream().sorted().toList();
        validateNodes(nodes);
    }

    private static void validateNodes(List<TreeNodeDefinition> nodes) {
        if (nodes.isEmpty() || nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException("Tree node count must be within 1 and " + MAX_NODES);
        }
        var byId = new TreeMap<ResourceLocation, TreeNodeDefinition>(ResourceLocation::compareNamespaced);
        var positions = new HashSet<GridPosition>();
        var grantIds = new HashSet<ResourceLocation>();
        for (TreeNodeDefinition node : nodes) {
            if (byId.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate tree node id " + node.id());
            }
            if (!positions.add(new GridPosition(node.row(), node.column()))) {
                throw new IllegalArgumentException("Duplicate tree node grid position");
            }
            for (TreeAttributeGrant grant : node.grants()) {
                if (!grantIds.add(grant.id())) {
                    throw new IllegalArgumentException("Duplicate tree grant id " + grant.id());
                }
            }
        }
        int edges = 0;
        long refundMutations = 0;
        var indegree = new HashMap<ResourceLocation, Integer>();
        var dependents = new HashMap<ResourceLocation, List<ResourceLocation>>();
        for (TreeNodeDefinition node : nodes) {
            var dependencies = new ArrayList<ResourceLocation>();
            dependencies.addAll(node.requires());
            dependencies.addAll(node.requiresAny());
            edges = Math.addExact(edges, dependencies.size());
            if (edges > MAX_EDGES) {
                throw new IllegalArgumentException("Tree prerequisite edge count exceeds its bound");
            }
            refundMutations = Math.addExact(refundMutations, 2L + node.grants().size());
            if (refundMutations > CascadePlan.MAX_TOTAL_MUTATIONS) {
                throw new IllegalArgumentException("Tree refund closure exceeds its atomic mutation bound");
            }
            indegree.put(node.id(), dependencies.size());
            for (ResourceLocation dependency : dependencies) {
                if (!byId.containsKey(dependency)) {
                    throw new IllegalArgumentException("Tree node " + node.id()
                            + " references missing same tree node " + dependency);
                }
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(node.id());
            }
        }
        var ready = new java.util.PriorityQueue<ResourceLocation>(ResourceLocation::compareNamespaced);
        indegree.forEach((node, degree) -> {
            if (degree == 0) {
                ready.add(node);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            ResourceLocation node = ready.remove();
            visited++;
            for (ResourceLocation dependent : dependents.getOrDefault(node, List.of())) {
                int degree = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                if (degree == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalArgumentException("Tree prerequisites contain a cycle");
        }
    }

    public Map<ResourceLocation, TreeNodeDefinition> nodesById() {
        var result = new LinkedHashMap<ResourceLocation, TreeNodeDefinition>();
        nodes.forEach(node -> result.put(node.id(), node));
        return Collections.unmodifiableMap(result);
    }

    private record GridPosition(int row, int column) {
    }
}
