package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ClassCatalog {
    public static final int MAX_SLOTS = 64;
    public static final int MAX_CLASSES = 64;
    public static final int MAX_SYNERGIES = 64;
    public static final int RESERVED_NON_GRANT_MUTATIONS = 8;
    public static final int MAX_TOTAL_GRANTS = TransactionStep.MAX_MUTATIONS
            - MAX_CLASSES - RESERVED_NON_GRANT_MUTATIONS;

    private final Map<ResourceLocation, ClassSlotDefinition> slots;
    private final Map<ResourceLocation, ClassDefinition> classes;
    private final Map<ResourceLocation, ClassSynergyDefinition> synergies;
    private final Map<ResourceLocation, List<ClassDefinition>> classesBySlot;

    private ClassCatalog(
            Map<ResourceLocation, ClassSlotDefinition> slots,
            Map<ResourceLocation, ClassDefinition> classes,
            Map<ResourceLocation, ClassSynergyDefinition> synergies
    ) {
        this.slots = immutable(slots);
        this.classes = immutable(classes);
        this.synergies = immutable(synergies);
        var grouped = new TreeMap<ResourceLocation, List<ClassDefinition>>(ResourceLocation::compareNamespaced);
        this.classes.values().forEach(definition -> grouped.computeIfAbsent(
                definition.slot(), ignored -> new ArrayList<>()).add(definition));
        var immutableGrouped = new LinkedHashMap<ResourceLocation, List<ClassDefinition>>();
        grouped.forEach((slot, definitions) -> immutableGrouped.put(slot, definitions.stream().sorted().toList()));
        this.classesBySlot = Collections.unmodifiableMap(immutableGrouped);
    }

    public static ClassCatalog from(CanonicalIr ir, SkillCatalog skills, TreeCatalog trees) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(trees, "trees");
        var slots = new TreeMap<ResourceLocation, ClassSlotDefinition>(ResourceLocation::compareNamespaced);
        var classes = new TreeMap<ResourceLocation, ClassDefinition>(ResourceLocation::compareNamespaced);
        ir.definitions().forEach((key, canonical) -> {
            if (key.kind().equals(DefinitionKinds.CLASS_SLOT)) {
                if (slots.size() >= MAX_SLOTS) {
                    throw new IllegalArgumentException("Class slot count exceeds " + MAX_SLOTS);
                }
                ClassSlotDefinition slot = ClassCanonicalCodec.decodeSlot(canonical);
                if (slots.putIfAbsent(slot.id(), slot) != null) {
                    throw new IllegalArgumentException("Duplicate class slot id " + slot.id());
                }
            } else if (key.kind().equals(DefinitionKinds.CLASS)) {
                if (classes.size() >= MAX_CLASSES) {
                    throw new IllegalArgumentException("Class count exceeds " + MAX_CLASSES);
                }
                ClassDefinition definition = ClassCanonicalCodec.decodeClass(canonical);
                if (classes.putIfAbsent(definition.id(), definition) != null) {
                    throw new IllegalArgumentException("Duplicate class id " + definition.id());
                }
            }
        });
        validateClasses(slots, classes, skills, trees);
        Map<ResourceLocation, ClassSynergyDefinition> synergies = synergies(classes, trees);
        validateClassGraph(classes);
        validateMutationBounds(classes, synergies);
        return new ClassCatalog(slots, classes, synergies);
    }

    private static void validateClasses(
            Map<ResourceLocation, ClassSlotDefinition> slots,
            Map<ResourceLocation, ClassDefinition> classes,
            SkillCatalog skills,
            TreeCatalog trees
    ) {
        var nodeIds = new HashSet<ResourceLocation>();
        trees.trees().values().forEach(tree -> tree.nodes().forEach(node -> nodeIds.add(node.id())));
        for (ClassDefinition definition : classes.values()) {
            ClassSlotDefinition slot = slots.get(definition.slot());
            if (slot == null) {
                throw new IllegalArgumentException("Class " + definition.id()
                        + " references missing class slot " + definition.slot());
            }
            if (definition.slotCost() > slot.capacity()) {
                throw new IllegalArgumentException("Class " + definition.id()
                        + " slot cost exceeds slot capacity " + definition.slot());
            }
            definition.minimumSkillLevels().forEach((skillId, minimum) -> {
                var skill = skills.skill(skillId).orElseThrow(() -> new IllegalArgumentException(
                        "Class " + definition.id() + " references missing skill " + skillId
                ));
                if (minimum > skill.curve().maxLevel()) {
                    throw new IllegalArgumentException("Class " + definition.id()
                            + " minimum level exceeds skill cap " + skillId);
                }
            });
            for (ResourceLocation node : definition.requiredNodes()) {
                if (!nodeIds.contains(node)) {
                    throw new IllegalArgumentException("Class " + definition.id()
                            + " references missing tree node " + node);
                }
            }
            for (ResourceLocation required : definition.requiredClasses()) {
                if (!classes.containsKey(required)) {
                    throw new IllegalArgumentException("Class " + definition.id()
                            + " references missing required class " + required);
                }
            }
            definition.selectionCost().ifPresent(cost -> validateCost(definition.id(), cost, skills));
            definition.respecCost().ifPresent(cost -> validateCost(definition.id(), cost, skills));
            validateGrantTargets(definition.id(), definition.grants(), classes, trees);
        }
    }

    private static void validateCost(
            ResourceLocation classId,
            ClassCurrencyCost cost,
            SkillCatalog skills
    ) {
        var currency = skills.currency(cost.currency()).orElseThrow(() -> new IllegalArgumentException(
                "Class " + classId + " references missing currency " + cost.currency()
        ));
        if (cost.amount() > currency.maximum()) {
            throw new IllegalArgumentException("Class " + classId
                    + " cost exceeds currency maximum " + cost.currency());
        }
    }

    private static Map<ResourceLocation, ClassSynergyDefinition> synergies(
            Map<ResourceLocation, ClassDefinition> classes,
            TreeCatalog trees
    ) {
        var result = new TreeMap<ResourceLocation, ClassSynergyDefinition>(ResourceLocation::compareNamespaced);
        for (ClassDefinition definition : classes.values()) {
            for (ClassSynergyDefinition synergy : definition.synergies()) {
                if (result.size() >= MAX_SYNERGIES) {
                    throw new IllegalArgumentException("Class synergy count exceeds " + MAX_SYNERGIES);
                }
                for (ResourceLocation required : synergy.requiredClasses()) {
                    if (!classes.containsKey(required)) {
                        throw new IllegalArgumentException("Class synergy " + synergy.id()
                                + " references missing class " + required);
                    }
                }
                validateGrantTargets(synergy.id(), synergy.grants(), classes, trees);
                if (result.putIfAbsent(synergy.id(), synergy) != null) {
                    throw new IllegalArgumentException("Duplicate class synergy id " + synergy.id());
                }
            }
        }
        return result;
    }

    private static void validateGrantTargets(
            ResourceLocation owner,
            List<ClassGrant> grants,
            Map<ResourceLocation, ClassDefinition> classes,
            TreeCatalog trees
    ) {
        for (ClassGrant grant : grants) {
            if (grant.type() == ClassGrantType.CLASS_ACCESS && !classes.containsKey(grant.targetId())) {
                throw new IllegalArgumentException("Class grant " + grant.id()
                        + " references missing class " + grant.targetId() + " from " + owner);
            }
            if (grant.type() == ClassGrantType.TREE_ACCESS && trees.tree(grant.targetId()).isEmpty()) {
                throw new IllegalArgumentException("Class grant " + grant.id()
                        + " references missing tree " + grant.targetId() + " from " + owner);
            }
        }
    }

    private static void validateClassGraph(Map<ResourceLocation, ClassDefinition> classes) {
        var indegree = new HashMap<ResourceLocation, Integer>();
        var dependents = new HashMap<ResourceLocation, List<ResourceLocation>>();
        for (ClassDefinition definition : classes.values()) {
            indegree.put(definition.id(), definition.requiredClasses().size());
            for (ResourceLocation required : definition.requiredClasses()) {
                dependents.computeIfAbsent(required, ignored -> new ArrayList<>()).add(definition.id());
            }
        }
        var ready = new java.util.PriorityQueue<ResourceLocation>(ResourceLocation::compareNamespaced);
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            ResourceLocation current = ready.remove();
            visited++;
            for (ResourceLocation dependent : dependents.getOrDefault(current, List.of())) {
                int degree = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                if (degree == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (visited != classes.size()) {
            throw new IllegalArgumentException("Class prerequisites contain a cycle");
        }
    }

    private static void validateMutationBounds(
            Map<ResourceLocation, ClassDefinition> classes,
            Map<ResourceLocation, ClassSynergyDefinition> synergies
    ) {
        var grantIds = new HashSet<ResourceLocation>();
        int totalGrants = 0;
        for (ClassDefinition definition : classes.values()) {
            for (ClassGrant grant : definition.grants()) {
                totalGrants = Math.addExact(totalGrants, 1);
                if (!grantIds.add(grant.id())) {
                    throw new IllegalArgumentException("Class grant id must be globally unique " + grant.id());
                }
            }
        }
        for (ClassSynergyDefinition synergy : synergies.values()) {
            for (ClassGrant grant : synergy.grants()) {
                totalGrants = Math.addExact(totalGrants, 1);
                if (!grantIds.add(grant.id())) {
                    throw new IllegalArgumentException("Class grant id must be globally unique " + grant.id());
                }
            }
        }
        if (totalGrants > MAX_TOTAL_GRANTS) {
            throw new IllegalArgumentException("Class and synergy grant count exceeds " + MAX_TOTAL_GRANTS);
        }
        long fullProjectionMutations = (long) classes.size() + totalGrants + RESERVED_NON_GRANT_MUTATIONS;
        if (fullProjectionMutations > TransactionStep.MAX_MUTATIONS
                || fullProjectionMutations > CascadePlan.MAX_TOTAL_MUTATIONS) {
            throw new IllegalArgumentException("Class reconciliation exceeds its atomic mutation bound");
        }
    }

    public Map<ResourceLocation, ClassSlotDefinition> slots() {
        return slots;
    }

    public Map<ResourceLocation, ClassDefinition> classes() {
        return classes;
    }

    public Map<ResourceLocation, ClassSynergyDefinition> synergies() {
        return synergies;
    }

    public Optional<ClassSlotDefinition> slot(ResourceLocation id) {
        return Optional.ofNullable(slots.get(id));
    }

    public Optional<ClassDefinition> classDefinition(ResourceLocation id) {
        return Optional.ofNullable(classes.get(id));
    }

    public Optional<ClassSynergyDefinition> synergy(ResourceLocation id) {
        return Optional.ofNullable(synergies.get(id));
    }

    public List<ClassDefinition> classesInSlot(ResourceLocation slot) {
        return classesBySlot.getOrDefault(slot, List.of());
    }

    public boolean conflicts(ResourceLocation first, ResourceLocation second) {
        ClassDefinition left = classDefinition(first).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + first));
        ClassDefinition right = classDefinition(second).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + second));
        return !Collections.disjoint(left.exclusiveTags(), right.exclusiveTags());
    }

    public List<ClassSynergyDefinition> activeSynergies(Set<ResourceLocation> selectedClasses) {
        Set<ResourceLocation> selected = Set.copyOf(Objects.requireNonNull(selectedClasses, "selectedClasses"));
        return synergies.values().stream()
                .filter(ClassSynergyDefinition::enabled)
                .filter(synergy -> selected.containsAll(synergy.requiredClasses()))
                .toList();
    }

    public String classLineageFingerprint(ResourceLocation classId) {
        ClassDefinition definition = classDefinition(classId).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + classId));
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                write(output, "progressiveskills-class-lineage-v1");
                write(output, definition.id().toString());
                write(output, definition.slot().toString());
                output.writeInt(definition.slotCost());
                output.writeInt(definition.grants().size());
                for (ClassGrant grant : definition.grants()) {
                    write(output, grant.id().toString());
                    write(output, grant.type().serializedName());
                    write(output, grant.targetType().toString());
                    write(output, grant.targetId().toString());
                    if (grant instanceof ClassSpellGrant spell) {
                        write(output, spell.learningPolicy().serializedName());
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in memory lineage failure", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static <T> Map<ResourceLocation, T> immutable(Map<ResourceLocation, T> values) {
        var sorted = new TreeMap<ResourceLocation, T>(ResourceLocation::compareNamespaced);
        sorted.putAll(values);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
