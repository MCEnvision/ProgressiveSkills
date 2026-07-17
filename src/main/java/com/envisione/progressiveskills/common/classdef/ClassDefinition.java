package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record ClassDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        boolean enabled,
        boolean accessRequired,
        ResourceLocation slot,
        int slotCost,
        Set<ResourceLocation> exclusiveTags,
        Map<ResourceLocation, Integer> minimumSkillLevels,
        List<ResourceLocation> requiredNodes,
        List<ResourceLocation> requiredClasses,
        Optional<ClassCurrencyCost> selectionCost,
        boolean respecAllowed,
        Optional<ClassCurrencyCost> respecCost,
        Optional<ClassStarterKit> starterKit,
        List<ClassGrant> grants,
        List<ClassSynergyDefinition> synergies
) implements Comparable<ClassDefinition> {
    public static final int MAX_SLOT_COST = ClassSlotDefinition.MAX_CAPACITY;
    public static final int MAX_EXCLUSIVE_TAGS = 32;
    public static final int MAX_MINIMUM_SKILLS = 32;
    public static final int MAX_REQUIRED_NODES = 64;
    public static final int MAX_REQUIRED_CLASSES = 32;
    public static final int MAX_TOTAL_PREREQUISITES = 64;
    public static final int MAX_GRANTS = 32;
    public static final int MAX_SYNERGIES = 16;

    public ClassDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        slot = StableId.requireValid(slot);
        if (slotCost < 0 || slotCost > MAX_SLOT_COST) {
            throw new IllegalArgumentException("Class slot cost must be within 0 and " + MAX_SLOT_COST);
        }
        exclusiveTags = stableIdSet(exclusiveTags, MAX_EXCLUSIVE_TAGS, "Class exclusive tag");
        minimumSkillLevels = minimumLevels(minimumSkillLevels);
        requiredNodes = stableIdList(requiredNodes, MAX_REQUIRED_NODES, "Class required node");
        requiredClasses = stableIdList(requiredClasses, MAX_REQUIRED_CLASSES, "Class required class");
        if (requiredNodes.size() + requiredClasses.size() > MAX_TOTAL_PREREQUISITES) {
            throw new IllegalArgumentException("Class prerequisite count exceeds " + MAX_TOTAL_PREREQUISITES);
        }
        if (requiredClasses.contains(id)) {
            throw new IllegalArgumentException("Class must not require itself");
        }
        Objects.requireNonNull(selectionCost, "selectionCost");
        Objects.requireNonNull(respecCost, "respecCost");
        if (!respecAllowed && respecCost.isPresent()) {
            throw new IllegalArgumentException("A class with disabled respec must not declare a respec cost");
        }
        Objects.requireNonNull(starterKit, "starterKit");
        if (starterKit.isPresent() && !starterKit.orElseThrow().receiptId().equals(starterKitReceiptId(id))) {
            throw new IllegalArgumentException("Class starter kit receipt id does not match its stable identity");
        }
        Objects.requireNonNull(grants, "grants");
        grants = grants.stream().sorted().toList();
        if (grants.size() > MAX_GRANTS) {
            throw new IllegalArgumentException("Class grant count exceeds " + MAX_GRANTS);
        }
        if (new HashSet<>(grants.stream().map(ClassGrant::id).toList()).size() != grants.size()) {
            throw new IllegalArgumentException("Class grant ids contain duplicates");
        }
        Objects.requireNonNull(synergies, "synergies");
        synergies = synergies.stream().sorted().toList();
        if (synergies.size() > MAX_SYNERGIES) {
            throw new IllegalArgumentException("Class synergy count exceeds " + MAX_SYNERGIES);
        }
        if (new HashSet<>(synergies.stream().map(ClassSynergyDefinition::id).toList()).size() != synergies.size()) {
            throw new IllegalArgumentException("Class synergy ids contain duplicates");
        }
        for (ClassSynergyDefinition synergy : synergies) {
            if (!synergy.requiredClasses().contains(id)) {
                throw new IllegalArgumentException("A nested class synergy must require its owning class");
            }
        }
    }

    public static ResourceLocation starterKitReceiptId(ResourceLocation classId) {
        ResourceLocation stable = StableId.requireValid(classId);
        return ResourceLocation.fromNamespaceAndPath(stable.getNamespace(), stable.getPath() + "/starter_kit");
    }

    public GrantSourceId grantSource(ClassGrant grant) {
        Objects.requireNonNull(grant, "grant");
        if (grants.stream().noneMatch(candidate -> candidate.id().equals(grant.id()))) {
            throw new IllegalArgumentException("Grant does not belong to class " + id);
        }
        return grant.source(DefinitionKinds.CLASS.id(), id);
    }

    private static Set<ResourceLocation> stableIdSet(Set<ResourceLocation> values, int maximum, String label) {
        Objects.requireNonNull(values, label);
        if (values.size() > maximum) {
            throw new IllegalArgumentException(label + " count exceeds " + maximum);
        }
        var sorted = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        values.forEach(value -> {
            if (!sorted.add(StableId.requireValid(value))) {
                throw new IllegalArgumentException(label + " contains duplicates");
            }
        });
        return Collections.unmodifiableSet(sorted);
    }

    private static List<ResourceLocation> stableIdList(List<ResourceLocation> values, int maximum, String label) {
        Objects.requireNonNull(values, label);
        if (values.size() > maximum) {
            throw new IllegalArgumentException(label + " count exceeds " + maximum);
        }
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
            throw new IllegalArgumentException("Class minimum skill count exceeds " + MAX_MINIMUM_SKILLS);
        }
        var sorted = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        values.forEach((skill, level) -> {
            ResourceLocation stable = StableId.requireValid(skill);
            if (level == null || level < 0) {
                throw new IllegalArgumentException("Class minimum skill level must not be negative");
            }
            sorted.put(stable, level);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    @Override
    public int compareTo(ClassDefinition other) {
        return id.compareNamespaced(other.id);
    }
}
