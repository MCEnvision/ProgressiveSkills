package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementMutation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PaidCostMutation;
import com.envisione.progressiveskills.common.transaction.PaidCostRecord;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.PurchaseInstanceId;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

public final class ClassProgression {
    public static final ResourceLocation SELECTION_OWNER_KIND = id("class_selection");
    public static final ResourceLocation ACTIVE_OWNER_KIND = id("class_activity");
    public static final ResourceLocation SYNERGY_OWNER_KIND = id("class_synergy");
    private static final ResourceLocation SELECT_ORIGIN = id("class_select");
    private static final ResourceLocation RESPEC_ORIGIN = id("class_respec");
    private static final ResourceLocation SWAP_ORIGIN = id("class_swap");
    private static final ResourceLocation RECONCILE_ORIGIN = id("class_reconcile");

    private ClassProgression() {
    }

    public static ChangePreview previewSelect(
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            ResourceLocation classId
    ) {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(snapshot, "snapshot");
        ClassDefinition definition = classes.classDefinition(classId).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + classId)
        );
        Set<ResourceLocation> before = selectedClasses(snapshot);
        var blockers = new ArrayList<String>();
        validateSelection(classes, skills, snapshot, definition, before, blockers);
        var after = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        after.addAll(before);
        after.add(definition.id());
        Map<ResourceLocation, Long> costs = costMap(definition.selectionCost());
        validateCosts(skills, snapshot, costs, blockers);
        Set<ResourceLocation> activeBefore = activeClasses(snapshot);
        DesiredState desired = desiredState(classes, snapshot, after);
        if (!blockers.isEmpty() || !desired.activeClasses().contains(definition.id())) {
            if (blockers.isEmpty()) {
                blockers.add("Class would be suspended by capacity or coexistence rules");
            }
        }
        List<ResourceLocation> affected = affected(before, after, activeBefore, desired.activeClasses());
        return preview(ChangeKind.SELECT, definition.id(), Optional.empty(), after, affected,
                costs, snapshot, null, blockers);
    }

    public static CascadePlan select(
            UUID actor,
            UUID target,
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation classId,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        ChangePreview preview = previewSelect(classes, skills, snapshot, classId);
        requireAllowed(preview);
        ClassDefinition selected = classes.classDefinition(classId).orElseThrow();
        return planChange(actor, target, classes, skills, snapshot, definition,
                preview, selectedClassesWith(snapshot, classId), Optional.empty(),
                Optional.of(selected), idempotencyKey, cause, SELECT_ORIGIN,
                "Select class");
    }

    public static ChangePreview previewRespec(
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation classId
    ) {
        Objects.requireNonNull(definition, "definition");
        ClassDefinition selected = classes.classDefinition(classId).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + classId)
        );
        Set<ResourceLocation> before = selectedClasses(snapshot);
        var blockers = new ArrayList<String>();
        if (!before.contains(classId)) {
            blockers.add("Class is not selected");
        }
        if (!selected.respecAllowed()) {
            blockers.add("Class respec is disabled");
        }
        var after = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        after.addAll(before);
        after.remove(classId);
        Map<ResourceLocation, Long> costs = costMap(selected.respecCost());
        validateCosts(skills, snapshot, costs, blockers);
        Set<ResourceLocation> activeBefore = activeClasses(snapshot);
        DesiredState desired = desiredState(classes, snapshot, after);
        List<ResourceLocation> affected = affected(before, after, activeBefore, desired.activeClasses());
        return preview(ChangeKind.RESPEC, classId, Optional.empty(), after, affected,
                costs, snapshot, definition, blockers);
    }

    public static CascadePlan respec(
            UUID actor,
            UUID target,
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation classId,
            String expectedDigest,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        ChangePreview preview = previewRespec(classes, skills, snapshot, definition, classId);
        requireAllowed(preview);
        requireDigest(preview, expectedDigest);
        ClassDefinition removed = classes.classDefinition(classId).orElseThrow();
        var selectedAfter = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        selectedAfter.addAll(selectedClasses(snapshot));
        selectedAfter.remove(classId);
        return planChange(actor, target, classes, skills, snapshot, definition,
                preview, selectedAfter, Optional.of(removed), Optional.empty(),
                idempotencyKey, cause, RESPEC_ORIGIN, "Respec class");
    }

    public static ChangePreview previewSwap(
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation removedClassId,
            ResourceLocation replacementClassId
    ) {
        Objects.requireNonNull(definition, "definition");
        if (removedClassId.equals(replacementClassId)) {
            throw new IllegalArgumentException("Class swap requires two different classes");
        }
        ClassDefinition removed = classes.classDefinition(removedClassId).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + removedClassId)
        );
        ClassDefinition replacement = classes.classDefinition(replacementClassId).orElseThrow(
                () -> new IllegalArgumentException("Unknown class " + replacementClassId)
        );
        Set<ResourceLocation> before = selectedClasses(snapshot);
        var blockers = new ArrayList<String>();
        if (!before.contains(removedClassId)) {
            blockers.add("Class to replace is not selected");
        }
        if (before.contains(replacementClassId)) {
            blockers.add("Replacement class is already selected");
        }
        if (!removed.slot().equals(replacement.slot())) {
            blockers.add("Class swap requires the same slot");
        }
        if (!removed.respecAllowed()) {
            blockers.add("Class respec is disabled");
        }
        classes.slot(removed.slot()).ifPresent(slot -> {
            if (slot.swapPolicy() == ClassSwapPolicy.DISABLED) {
                blockers.add("Class slot swaps are disabled");
            }
        });
        var base = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        base.addAll(before);
        base.remove(removedClassId);
        validateSelection(classes, skills, snapshot, replacement, base, blockers);
        base.add(replacementClassId);
        Map<ResourceLocation, Long> costs = mergeCosts(
                costMap(removed.respecCost()), costMap(replacement.selectionCost())
        );
        validateCosts(skills, snapshot, costs, blockers);
        Set<ResourceLocation> activeBefore = activeClasses(snapshot);
        DesiredState desired = desiredState(classes, snapshot, base);
        if (!desired.activeClasses().contains(replacementClassId) && blockers.isEmpty()) {
            blockers.add("Replacement class would be suspended by its post swap requirements");
        }
        List<ResourceLocation> affected = affected(before, base, activeBefore, desired.activeClasses());
        return preview(ChangeKind.SWAP, removedClassId, Optional.of(replacementClassId), base,
                affected, costs, snapshot, definition, blockers);
    }

    public static CascadePlan swap(
            UUID actor,
            UUID target,
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation removedClassId,
            ResourceLocation replacementClassId,
            String expectedDigest,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        ChangePreview preview = previewSwap(
                classes, skills, snapshot, definition, removedClassId, replacementClassId
        );
        requireAllowed(preview);
        requireDigest(preview, expectedDigest);
        var selectedAfter = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        selectedAfter.addAll(selectedClasses(snapshot));
        selectedAfter.remove(removedClassId);
        selectedAfter.add(replacementClassId);
        return planChange(actor, target, classes, skills, snapshot, definition,
                preview, selectedAfter,
                Optional.of(classes.classDefinition(removedClassId).orElseThrow()),
                Optional.of(classes.classDefinition(replacementClassId).orElseThrow()),
                idempotencyKey, cause, SWAP_ORIGIN, "Swap class");
    }

    public static Optional<CascadePlan> reconcile(
            UUID target,
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(skills, "skills");
        Set<ResourceLocation> selected = selectedClasses(snapshot);
        DesiredState desired = desiredState(classes, snapshot, selected);
        List<EntitlementMutation> entitlements = entitlementMutations(snapshot, desired.ownership());
        if (entitlements.isEmpty()) {
            return Optional.empty();
        }
        var preview = preview(
                ChangeKind.RECONCILE,
                id("reconcile"),
                Optional.empty(),
                selected,
                affected(Set.of(), Set.of(), activeClasses(snapshot), desired.activeClasses()),
                Map.of(),
                snapshot,
                definition,
                List.of()
        );
        IdempotencyKey key = new IdempotencyKey(
                "phase11/reconcile/" + definition.generation() + "/"
                        + snapshot.stateRevision() + "/" + target
        );
        return Optional.of(buildPlan(
                target, target, snapshot, definition, key, ProgressionCause.RECONCILE,
                "Reconcile classes", RECONCILE_ORIGIN, List.of(), entitlements,
                List.of(), List.of()
        ));
    }

    public static Set<ResourceLocation> selectedClasses(ProgressionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var selected = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.ownership().forEach((key, owners) -> {
            if (key.targetType().equals(ClassEntitlementTypes.SELECTED)
                    && owners.containsKey(selectionSource(key.targetId()))) {
                selected.add(key.targetId());
            }
        });
        snapshot.paidCosts().keySet().stream()
                .filter(instance -> instance.ownerKind().equals(DefinitionKinds.CLASS.id()))
                .filter(instance -> instance.rank() == 1)
                .filter(instance -> instance.ownerId().equals(instance.purchaseId()))
                .forEach(instance -> selected.add(instance.ownerId()));
        return Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    public static Set<ResourceLocation> activeClasses(ProgressionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var active = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.ownership().forEach((key, owners) -> {
            if (key.targetType().equals(ClassEntitlementTypes.ACTIVE)
                    && owners.containsKey(activeSource(key.targetId()))) {
                active.add(key.targetId());
            }
        });
        return Collections.unmodifiableSet(new LinkedHashSet<>(active));
    }

    private static CascadePlan planChange(
            UUID actor,
            UUID target,
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ChangePreview preview,
            Set<ResourceLocation> selectedAfter,
            Optional<ClassDefinition> removed,
            Optional<ClassDefinition> added,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause,
            ResourceLocation origin,
            String reason
    ) {
        DesiredState desired = desiredState(classes, snapshot, selectedAfter);
        List<EntitlementMutation> entitlements = entitlementMutations(snapshot, desired.ownership());
        List<BalanceMutation> balances = balanceMutations(skills, snapshot, preview.costBalances());
        var paid = new ArrayList<PaidCostMutation>();
        removed.map(ClassDefinition::id).map(ClassProgression::instance)
                .map(snapshot.paidCosts()::get).filter(Objects::nonNull)
                .ifPresent(record -> paid.add(PaidCostMutation.remove(record)));
        if (added.isPresent() && added.orElseThrow().selectionCost().isPresent()) {
            ClassDefinition value = added.orElseThrow();
            ClassCurrencyCost cost = value.selectionCost().orElseThrow();
            PurchaseInstanceId instance = instance(value.id());
            if (snapshot.paidCosts().containsKey(instance)) {
                throw new IllegalArgumentException("Class already has historical selection evidence");
            }
            var sources = new TreeSet<GrantSourceId>();
            value.grants().forEach(grant -> sources.add(grant.source(DefinitionKinds.CLASS.id(), value.id())));
            paid.add(PaidCostMutation.insert(new PaidCostRecord(
                    instance,
                    TransactionId.derive(target, idempotencyKey),
                    definition,
                    classes.classLineageFingerprint(value.id()),
                    Map.of(cost.currency(), cost.amount()),
                    sources
            )));
        }
        List<TransitionAction> actions = added.map(ClassProgression::starterActions).orElse(List.of());
        return buildPlan(actor, target, snapshot, definition, idempotencyKey, cause,
                reason, origin, balances, entitlements, paid, actions);
    }

    private static CascadePlan buildPlan(
            UUID actor,
            UUID target,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause,
            String reason,
            ResourceLocation origin,
            List<BalanceMutation> balances,
            List<EntitlementMutation> entitlements,
            List<PaidCostMutation> paid,
            List<TransitionAction> actions
    ) {
        int rootCapacity = TransactionStep.MAX_MUTATIONS - balances.size() - paid.size();
        if (rootCapacity < 0) {
            throw new IllegalArgumentException("Class change exceeds root mutation capacity");
        }
        int firstCount = Math.min(rootCapacity, entitlements.size());
        var steps = new ArrayList<TransactionStep>();
        steps.add(new TransactionStep(
                origin,
                balances,
                entitlements.subList(0, firstCount),
                paid,
                actions
        ));
        for (int start = firstCount; start < entitlements.size(); start += TransactionStep.MAX_MUTATIONS) {
            int end = Math.min(entitlements.size(), start + TransactionStep.MAX_MUTATIONS);
            steps.add(new TransactionStep(
                    origin, List.of(), entitlements.subList(start, end), List.of(), List.of()
            ));
        }
        TransactionPlan transaction = new TransactionPlan(
                actor, target, idempotencyKey, snapshot.stateRevision(), definition,
                cause, reason, steps.getFirst()
        );
        return new CascadePlan(transaction, steps.subList(1, steps.size()));
    }

    private static DesiredState desiredState(
            ClassCatalog classes,
            ProgressionSnapshot snapshot,
            Set<ResourceLocation> selected
    ) {
        List<ClassDefinition> order = topologicalOrder(classes);
        Set<ResourceLocation> ownedNodes = ownedNodes(snapshot);
        Set<ResourceLocation> overCapacitySlots = overCapacitySlots(classes, selected);
        Set<ResourceLocation> conflictedClasses = conflictedClasses(classes, selected);
        var active = new LinkedHashSet<ResourceLocation>();
        boolean changed;
        do {
            changed = false;
            for (ClassDefinition definition : order) {
                if (active.contains(definition.id()) || !selected.contains(definition.id())
                        || !definition.enabled() || overCapacitySlots.contains(definition.slot())
                        || conflictedClasses.contains(definition.id())
                        || !compatibleLineage(classes, snapshot, definition)) {
                    continue;
                }
                boolean levels = definition.minimumSkillLevels().entrySet().stream().allMatch(entry ->
                        snapshot.balances().getOrDefault(SkillStateIds.level(entry.getKey()), 0L)
                                >= entry.getValue());
                if (!levels || !ownedNodes.containsAll(definition.requiredNodes())
                        || !active.containsAll(definition.requiredClasses())) {
                    continue;
                }
                active.add(definition.id());
                changed = true;
            }
        } while (changed);
        var desired = new TreeMap<Binding, EntitlementContribution>();
        for (ResourceLocation classId : selected) {
            putDesired(desired, selectionKey(classId), selectionSource(classId),
                    new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION));
        }
        for (ResourceLocation classId : active) {
            ClassDefinition definition = classes.classDefinition(classId).orElseThrow();
            putDesired(desired, activeKey(classId), activeSource(classId),
                    new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION));
            for (ClassGrant grant : definition.grants()) {
                putDesired(desired, grant.entitlementKey(),
                        grant.source(DefinitionKinds.CLASS.id(), definition.id()), grant.contribution());
            }
        }
        for (ClassSynergyDefinition synergy : classes.activeSynergies(active)) {
            for (ClassGrant grant : synergy.grants()) {
                putDesired(desired, grant.entitlementKey(),
                        grant.source(SYNERGY_OWNER_KIND, synergy.id()), grant.contribution());
            }
        }
        return new DesiredState(
                Collections.unmodifiableSet(new LinkedHashSet<>(active)),
                Collections.unmodifiableMap(new LinkedHashMap<>(desired))
        );
    }

    private static Set<ResourceLocation> overCapacitySlots(
            ClassCatalog classes,
            Set<ResourceLocation> selected
    ) {
        var usage = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        selected.stream().map(classes::classDefinition).flatMap(Optional::stream).forEach(definition ->
                usage.merge(definition.slot(), (long) definition.slotCost(), Math::addExact));
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        usage.forEach((slotId, used) -> {
            if (used > classes.slot(slotId).orElseThrow().capacity()) {
                result.add(slotId);
            }
        });
        return result;
    }

    private static Set<ResourceLocation> conflictedClasses(
            ClassCatalog classes,
            Set<ResourceLocation> selected
    ) {
        List<ResourceLocation> known = selected.stream()
                .filter(id -> classes.classDefinition(id).isPresent())
                .sorted(ResourceLocation::compareNamespaced).toList();
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        for (int left = 0; left < known.size(); left++) {
            for (int right = left + 1; right < known.size(); right++) {
                if (classes.conflicts(known.get(left), known.get(right))) {
                    result.add(known.get(left));
                    result.add(known.get(right));
                }
            }
        }
        return result;
    }

    private static boolean compatibleLineage(
            ClassCatalog classes,
            ProgressionSnapshot snapshot,
            ClassDefinition definition
    ) {
        PaidCostRecord record = snapshot.paidCosts().get(instance(definition.id()));
        return record == null || record.ownerLineage().equals(
                classes.classLineageFingerprint(definition.id())
        );
    }

    private static List<ClassDefinition> topologicalOrder(ClassCatalog classes) {
        var indegree = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        var dependents = new TreeMap<ResourceLocation, List<ResourceLocation>>(ResourceLocation::compareNamespaced);
        classes.classes().values().forEach(definition -> {
            indegree.put(definition.id(), definition.requiredClasses().size());
            definition.requiredClasses().forEach(required ->
                    dependents.computeIfAbsent(required, ignored -> new ArrayList<>()).add(definition.id()));
        });
        var ready = new java.util.PriorityQueue<ResourceLocation>(ResourceLocation::compareNamespaced);
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        var result = new ArrayList<ClassDefinition>();
        while (!ready.isEmpty()) {
            ResourceLocation id = ready.remove();
            result.add(classes.classDefinition(id).orElseThrow());
            dependents.getOrDefault(id, List.of()).stream()
                    .sorted(ResourceLocation::compareNamespaced).forEach(dependent -> {
                        int degree = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                        if (degree == 0) {
                            ready.add(dependent);
                        }
                    });
        }
        if (result.size() != classes.classes().size()) {
            throw new IllegalArgumentException("Class prerequisites contain a cycle");
        }
        return List.copyOf(result);
    }

    private static void validateSelection(
            ClassCatalog classes,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            ClassDefinition definition,
            Set<ResourceLocation> selected,
            List<String> blockers
    ) {
        if (!definition.enabled()) {
            blockers.add("Class is disabled");
        }
        if (selected.contains(definition.id())) {
            blockers.add("Class is already selected");
        }
        if (definition.accessRequired() && snapshot.projectedValues().getOrDefault(
                new EntitlementKey(
                        ClassGrantType.CLASS_ACCESS.entitlementType().orElseThrow(), definition.id()), 0L
        ) <= 0) {
            blockers.add("Class access is locked");
        }
        definition.minimumSkillLevels().forEach((skill, required) -> {
            long actual = snapshot.balances().getOrDefault(SkillStateIds.level(skill), 0L);
            if (actual < required) {
                blockers.add("Requires " + skill + " level " + required + ", current " + actual);
            }
        });
        Set<ResourceLocation> nodes = ownedNodes(snapshot);
        definition.requiredNodes().stream().filter(node -> !nodes.contains(node))
                .forEach(node -> blockers.add("Requires tree node " + node));
        Set<ResourceLocation> active = activeClasses(snapshot);
        definition.requiredClasses().stream().filter(required -> !active.contains(required))
                .forEach(required -> blockers.add("Requires active class " + required));
        ClassSlotDefinition slot = classes.slot(definition.slot()).orElseThrow();
        long used = selected.stream().map(classes::classDefinition).flatMap(Optional::stream)
                .filter(value -> value.slot().equals(slot.id()))
                .mapToLong(ClassDefinition::slotCost).sum();
        if (used + definition.slotCost() > slot.capacity()) {
            blockers.add("Class slot capacity would be exceeded");
        }
        selected.stream().filter(other -> classes.classDefinition(other).isPresent())
                .filter(other -> classes.conflicts(other, definition.id()))
                .forEach(other -> blockers.add("Conflicts with selected class " + other));
        if (snapshot.paidCosts().containsKey(instance(definition.id()))) {
            blockers.add("Class has existing historical selection evidence");
        }
        definition.selectionCost().ifPresent(cost -> {
            if (skills.currency(cost.currency()).isEmpty()) {
                blockers.add("Selection currency is unavailable " + cost.currency());
            }
        });
    }

    private static void validateCosts(
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            Map<ResourceLocation, Long> costs,
            List<String> blockers
    ) {
        costs.forEach((currencyId, amount) -> {
            Optional<CurrencyDefinition> currency = skills.currency(currencyId);
            if (currency.isEmpty()) {
                blockers.add("Currency is unavailable " + currencyId);
                return;
            }
            try {
                long balance = currentBalance(snapshot, currency.orElseThrow());
                long after = Math.subtractExact(balance, amount);
                if (after < currency.orElseThrow().minimum()) {
                    blockers.add("Class cost would leave " + currencyId + " below its minimum");
                }
            } catch (ArithmeticException exception) {
                blockers.add("Class cost is outside the supported range for " + currencyId);
            }
        });
    }

    private static List<BalanceMutation> balanceMutations(
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            Map<ResourceLocation, Long> costs
    ) {
        var result = new ArrayList<BalanceMutation>();
        costs.forEach((currencyId, amount) -> {
            CurrencyDefinition currency = skills.currency(currencyId).orElseThrow();
            long stored = snapshot.balances().getOrDefault(currencyId, 0L);
            long current = currentBalance(snapshot, currency);
            long after = Math.subtractExact(current, amount);
            result.add(new BalanceMutation(
                    currencyId,
                    Math.subtractExact(after, stored),
                    currency.minimum(),
                    currency.maximum()
            ));
        });
        return List.copyOf(result);
    }

    private static List<EntitlementMutation> entitlementMutations(
            ProgressionSnapshot snapshot,
            Map<Binding, EntitlementContribution> desired
    ) {
        var current = new TreeMap<Binding, EntitlementContribution>();
        snapshot.ownership().forEach((key, owners) -> owners.forEach((source, contribution) -> {
            if (managed(source)) {
                current.put(new Binding(key, source), contribution);
            }
        }));
        var keys = new TreeSet<Binding>();
        keys.addAll(current.keySet());
        keys.addAll(desired.keySet());
        var result = new ArrayList<EntitlementMutation>();
        for (Binding binding : keys) {
            EntitlementContribution before = current.get(binding);
            EntitlementContribution after = desired.get(binding);
            if (Objects.equals(before, after)) {
                continue;
            }
            result.add(after == null
                    ? EntitlementMutation.revoke(binding.key(), binding.source())
                    : EntitlementMutation.grant(
                            binding.key(), binding.source(), after.value(), after.resolver()
                    ));
        }
        return List.copyOf(result);
    }

    private static boolean managed(GrantSourceId source) {
        return source.ownerKind().equals(SELECTION_OWNER_KIND)
                || source.ownerKind().equals(ACTIVE_OWNER_KIND)
                || source.ownerKind().equals(DefinitionKinds.CLASS.id())
                || source.ownerKind().equals(SYNERGY_OWNER_KIND);
    }

    private static void putDesired(
            Map<Binding, EntitlementContribution> desired,
            EntitlementKey key,
            GrantSourceId source,
            EntitlementContribution contribution
    ) {
        if (desired.putIfAbsent(new Binding(key, source), contribution) != null) {
            throw new IllegalArgumentException("Duplicate desired class entitlement source");
        }
    }

    private static List<TransitionAction> starterActions(ClassDefinition definition) {
        if (definition.starterKit().isEmpty()) {
            return List.of();
        }
        ClassStarterKit kit = definition.starterKit().orElseThrow();
        return List.of(new TransitionAction(
                ClassStarterKitAction.TYPE,
                new GrantSourceId(
                        DefinitionKinds.CLASS.id(),
                        definition.id(),
                        kit.receiptId()
                ),
                ClassStarterKitAction.encode(kit),
                kit.items().size(),
                RepeatPolicy.ONCE_PER_CHARACTER,
                DeliveryContract.EFFECTIVELY_ONCE,
                TransitionFailurePolicy.STOP
        ));
    }

    private static Set<ResourceLocation> ownedNodes(ProgressionSnapshot snapshot) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.paidCosts().keySet().stream()
                .filter(instance -> instance.ownerKind().equals(DefinitionKinds.TREE.id()))
                .filter(instance -> instance.rank() == 1)
                .forEach(instance -> result.add(instance.purchaseId()));
        return result;
    }

    private static Set<ResourceLocation> selectedClassesWith(
            ProgressionSnapshot snapshot,
            ResourceLocation added
    ) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        result.addAll(selectedClasses(snapshot));
        result.add(added);
        return result;
    }

    private static EntitlementKey selectionKey(ResourceLocation classId) {
        return new EntitlementKey(ClassEntitlementTypes.SELECTED, classId);
    }

    private static EntitlementKey activeKey(ResourceLocation classId) {
        return new EntitlementKey(ClassEntitlementTypes.ACTIVE, classId);
    }

    private static GrantSourceId selectionSource(ResourceLocation classId) {
        return new GrantSourceId(SELECTION_OWNER_KIND, classId, nested(classId, "selected"));
    }

    private static GrantSourceId activeSource(ResourceLocation classId) {
        return new GrantSourceId(ACTIVE_OWNER_KIND, classId, nested(classId, "active"));
    }

    private static ResourceLocation nested(ResourceLocation owner, String child) {
        return ResourceLocation.fromNamespaceAndPath(owner.getNamespace(), owner.getPath() + "/" + child);
    }

    private static PurchaseInstanceId instance(ResourceLocation classId) {
        return new PurchaseInstanceId(DefinitionKinds.CLASS.id(), classId, classId, 1);
    }

    private static Map<ResourceLocation, Long> costMap(Optional<ClassCurrencyCost> cost) {
        return cost.<Map<ResourceLocation, Long>>map(value -> Map.of(value.currency(), value.amount()))
                .orElse(Map.of());
    }

    private static Map<ResourceLocation, Long> mergeCosts(
            Map<ResourceLocation, Long> first,
            Map<ResourceLocation, Long> second
    ) {
        var result = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        first.forEach((id, amount) -> result.merge(id, amount, Math::addExact));
        second.forEach((id, amount) -> result.merge(id, amount, Math::addExact));
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static long currentBalance(ProgressionSnapshot snapshot, CurrencyDefinition currency) {
        return snapshot.balances().getOrDefault(currency.id(), currency.initial());
    }

    private static List<ResourceLocation> affected(
            Set<ResourceLocation> selectedBefore,
            Set<ResourceLocation> selectedAfter,
            Set<ResourceLocation> activeBefore,
            Set<ResourceLocation> activeAfter
    ) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        symmetricDifference(result, selectedBefore, selectedAfter);
        symmetricDifference(result, activeBefore, activeAfter);
        return List.copyOf(result);
    }

    private static void symmetricDifference(
            Set<ResourceLocation> target,
            Set<ResourceLocation> first,
            Set<ResourceLocation> second
    ) {
        first.stream().filter(value -> !second.contains(value)).forEach(target::add);
        second.stream().filter(value -> !first.contains(value)).forEach(target::add);
    }

    private static ChangePreview preview(
            ChangeKind kind,
            ResourceLocation classId,
            Optional<ResourceLocation> replacementClassId,
            Set<ResourceLocation> selectedAfter,
            List<ResourceLocation> affected,
            Map<ResourceLocation, Long> costs,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            List<String> blockers
    ) {
        String digest = digest(kind, classId, replacementClassId, selectedAfter,
                affected, costs, snapshot, definition);
        return new ChangePreview(
                kind, classId, replacementClassId, affected, costs, digest, blockers
        );
    }

    private static String digest(
            ChangeKind kind,
            ResourceLocation classId,
            Optional<ResourceLocation> replacementClassId,
            Set<ResourceLocation> selectedAfter,
            List<ResourceLocation> affected,
            Map<ResourceLocation, Long> costs,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition
    ) {
        var text = new StringBuilder();
        text.append("progressiveskills-class-preview-v1\n")
                .append(kind).append('\n').append(classId).append('\n')
                .append(replacementClassId.map(Object::toString).orElse("")).append('\n')
                .append(snapshot.stateRevision()).append('\n');
        if (definition != null) {
            text.append(definition.generation()).append('\n')
                    .append(definition.semanticDigest()).append('\n');
        }
        selectedAfter.forEach(value -> text.append("selected ").append(value).append('\n'));
        affected.forEach(value -> text.append("affected ").append(value).append('\n'));
        costs.forEach((currency, amount) -> text.append("cost ").append(currency)
                .append(' ').append(amount).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void requireAllowed(ChangePreview preview) {
        if (!preview.allowed()) {
            throw new IllegalArgumentException(preview.blockers().getFirst());
        }
    }

    private static void requireDigest(ChangePreview preview, String expectedDigest) {
        Objects.requireNonNull(expectedDigest, "expectedDigest");
        if (!preview.digest().equals(expectedDigest)) {
            throw new IllegalArgumentException("Class change preview is stale");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    public enum ChangeKind {
        SELECT,
        RESPEC,
        SWAP,
        RECONCILE
    }

    public record ChangePreview(
            ChangeKind kind,
            ResourceLocation classId,
            Optional<ResourceLocation> replacementClassId,
            List<ResourceLocation> affectedClasses,
            Map<ResourceLocation, Long> costBalances,
            String digest,
            List<String> blockers
    ) {
        public ChangePreview {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(classId, "classId");
            Objects.requireNonNull(replacementClassId, "replacementClassId");
            affectedClasses = List.copyOf(Objects.requireNonNull(affectedClasses, "affectedClasses"));
            costBalances = Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(
                    Objects.requireNonNull(costBalances, "costBalances")
            )));
            Objects.requireNonNull(digest, "digest");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }
    }

    private record Binding(EntitlementKey key, GrantSourceId source) implements Comparable<Binding> {
        private Binding {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
        }

        @Override
        public int compareTo(Binding other) {
            int keyOrder = key.compareTo(other.key);
            return keyOrder != 0 ? keyOrder : source.compareTo(other.source);
        }
    }

    private record DesiredState(
            Set<ResourceLocation> activeClasses,
            Map<Binding, EntitlementContribution> ownership
    ) {
    }
}
