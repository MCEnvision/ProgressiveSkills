package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record TreeNodeDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        long cost,
        int row,
        int column,
        List<ResourceLocation> requires,
        List<ResourceLocation> requiresAny,
        Map<ResourceLocation, Integer> minimumSkillLevels,
        List<TreeAttributeGrant> grants
) implements Comparable<TreeNodeDefinition> {
    public static final int MAX_GRID_COORDINATE = 4096;
    public static final int MAX_PREREQUISITES = 64;
    public static final int MAX_MINIMUM_SKILLS = 32;
    public static final int MAX_GRANTS = 32;

    public TreeNodeDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        if (cost <= 0) {
            throw new IllegalArgumentException("Tree node cost must be positive");
        }
        if (Math.abs((long) row) > MAX_GRID_COORDINATE || Math.abs((long) column) > MAX_GRID_COORDINATE) {
            throw new IllegalArgumentException("Tree node grid position exceeds its bound");
        }
        requires = stableIds(requires, "Tree node requires");
        requiresAny = stableIds(requiresAny, "Tree node requires_any");
        if (requires.size() + requiresAny.size() > MAX_PREREQUISITES) {
            throw new IllegalArgumentException("Tree node prerequisite count exceeds its bound");
        }
        minimumSkillLevels = minimumLevels(minimumSkillLevels);
        grants = Objects.requireNonNull(grants, "grants").stream().sorted().toList();
        if (grants.size() > MAX_GRANTS) {
            throw new IllegalArgumentException("Tree node grant count exceeds its bound");
        }
        if (new HashSet<>(grants.stream().map(TreeAttributeGrant::id).toList()).size() != grants.size()) {
            throw new IllegalArgumentException("Duplicate tree attribute grant id");
        }
    }

    private static List<ResourceLocation> stableIds(List<ResourceLocation> values, String label) {
        Objects.requireNonNull(values, label);
        var sorted = values.stream().map(StableId::requireValid)
                .sorted(ResourceLocation::compareNamespaced).toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException(label + " contains duplicates");
        }
        return sorted;
    }

    private static Map<ResourceLocation, Integer> minimumLevels(Map<ResourceLocation, Integer> values) {
        Objects.requireNonNull(values, "minimumSkillLevels");
        if (values.size() > MAX_MINIMUM_SKILLS) {
            throw new IllegalArgumentException("Tree node minimum skill count exceeds its bound");
        }
        var sorted = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        values.forEach((skill, level) -> {
            ResourceLocation stable = StableId.requireValid(skill);
            if (level == null || level < 0) {
                throw new IllegalArgumentException("Tree node minimum skill level must not be negative");
            }
            sorted.put(stable, level);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    @Override
    public int compareTo(TreeNodeDefinition other) {
        return id.compareNamespaced(other.id);
    }
}
