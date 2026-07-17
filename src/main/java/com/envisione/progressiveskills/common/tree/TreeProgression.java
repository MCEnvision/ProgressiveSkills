package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
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
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
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
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

public final class TreeProgression {
    private static final ResourceLocation PURCHASE_ORIGIN = id("tree_purchase");
    private static final ResourceLocation REFUND_ORIGIN = id("tree_refund");
    private static final ResourceLocation RECONCILE_ORIGIN = id("tree_reconcile");

    private TreeProgression() {
    }

    public static PurchasePreview previewPurchase(
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(snapshot, "snapshot");
        TreeDefinition tree = trees.tree(treeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tree " + treeId)
        );
        TreeNodeDefinition node = trees.node(treeId, nodeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tree node " + nodeId)
        );
        var blockers = new ArrayList<String>();
        if (!tree.enabled()) {
            blockers.add("Tree is disabled");
        }
        if (snapshot.paidCosts().containsKey(instance(tree, node))) {
            blockers.add("Node is already owned");
        }
        Set<ResourceLocation> owned = ownedNodes(snapshot, tree.id());
        for (ResourceLocation required : node.requires()) {
            if (!owned.contains(required)) {
                blockers.add("Requires " + required);
            }
        }
        if (!node.requiresAny().isEmpty() && Collections.disjoint(owned, node.requiresAny())) {
            blockers.add("Requires one of " + String.join(", ", node.requiresAny().stream()
                    .map(ResourceLocation::toString).toList()));
        }
        node.minimumSkillLevels().forEach((skillId, required) -> {
            long actual = snapshot.balances().getOrDefault(SkillStateIds.level(skillId), 0L);
            if (actual < required) {
                blockers.add("Requires " + skillId + " level " + required + ", current " + actual);
            }
        });
        CurrencyDefinition currency = skills.currency(tree.currency()).orElseThrow(
                () -> new IllegalArgumentException("Tree references missing currency " + tree.currency())
        );
        long balance = currentBalance(snapshot, currency);
        try {
            long after = Math.subtractExact(balance, node.cost());
            if (after < currency.minimum()) {
                blockers.add("Purchase would leave " + currency.id() + " below " + currency.minimum()
                        + ", current " + balance);
            }
        } catch (ArithmeticException exception) {
            blockers.add("Purchase balance is outside the supported range for " + currency.id());
        }
        return new PurchasePreview(
                tree.id(), node.id(), currency.id(), node.cost(), balance, List.copyOf(blockers)
        );
    }

    public static CascadePlan purchase(
            UUID actor,
            UUID target,
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation treeId,
            ResourceLocation nodeId,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(cause, "cause");
        PurchasePreview preview = previewPurchase(trees, skills, snapshot, treeId, nodeId);
        if (!preview.allowed()) {
            throw new IllegalArgumentException(preview.blockers().getFirst());
        }
        TreeDefinition tree = trees.tree(treeId).orElseThrow();
        TreeNodeDefinition node = trees.node(treeId, nodeId).orElseThrow();
        CurrencyDefinition currency = skills.currency(tree.currency()).orElseThrow();
        long delta = snapshot.balances().containsKey(currency.id())
                ? -node.cost() : Math.subtractExact(currency.initial(), node.cost());
        var balances = new ArrayList<BalanceMutation>();
        if (delta != 0 || !snapshot.balances().containsKey(currency.id())) {
            balances.add(new BalanceMutation(currency.id(), delta, currency.minimum(), currency.maximum()));
        }
        var entitlements = new ArrayList<EntitlementMutation>();
        var sources = new TreeSet<GrantSourceId>();
        for (TreeAttributeGrant grant : node.grants()) {
            GrantSourceId source = source(node, grant);
            sources.add(source);
            entitlements.add(EntitlementMutation.grant(
                    key(grant), source, grant.valueUnits(), EntitlementResolver.ADDITIVE
            ));
        }
        var record = new PaidCostRecord(
                instance(tree, node),
                TransactionId.derive(target, idempotencyKey),
                definition,
                trees.nodeLineageFingerprint(tree.id(), node.id()),
                Map.of(currency.id(), node.cost()),
                sources
        );
        var step = new TransactionStep(
                PURCHASE_ORIGIN,
                balances,
                entitlements,
                List.of(PaidCostMutation.insert(record)),
                List.of()
        );
        return CascadePlan.single(new TransactionPlan(
                actor,
                target,
                idempotencyKey,
                snapshot.stateRevision(),
                definition,
                cause,
                "Purchase tree node",
                step
        ));
    }

    public static RefundPreview previewRefund(
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        TreeDefinition tree = trees.tree(treeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tree " + treeId)
        );
        trees.node(treeId, nodeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tree node " + nodeId)
        );
        PurchaseInstanceId selected = instance(tree.id(), nodeId);
        var blockers = new ArrayList<String>();
        if (!snapshot.paidCosts().containsKey(selected)) {
            blockers.add("Node is not owned");
            return new RefundPreview(
                    tree.id(), nodeId, List.of(), Map.of(), "0".repeat(64), blockers
            );
        }
        Set<ResourceLocation> closure = refundClosure(tree, ownedNodes(snapshot, tree.id()), Set.of(nodeId));
        List<ResourceLocation> order = reverseTopological(tree, closure);
        var totals = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        for (ResourceLocation affected : order) {
            PaidCostRecord record = snapshot.paidCosts().get(instance(tree.id(), affected));
            if (record == null) {
                blockers.add("Missing historical paid cost for " + affected);
                continue;
            }
            record.paidBalances().forEach((currencyId, amount) ->
                    totals.merge(currencyId, amount, Math::addExact));
        }
        totals.forEach((currencyId, amount) -> {
            Optional<CurrencyDefinition> currency = skills.currency(currencyId);
            if (currency.isEmpty()) {
                blockers.add("Historical currency is unavailable " + currencyId);
                return;
            }
            long balance = currentBalance(snapshot, currency.orElseThrow());
            long after = Math.addExact(balance, amount);
            if (after > currency.orElseThrow().maximum()) {
                blockers.add("Refund exceeds the currency maximum for " + currencyId);
            }
        });
        String digest = refundDigest(tree.id(), nodeId, order, snapshot, definition);
        return new RefundPreview(tree.id(), nodeId, order, totals, digest, blockers);
    }

    public static CascadePlan refund(
            UUID actor,
            UUID target,
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation treeId,
            ResourceLocation nodeId,
            String expectedDigest,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expectedDigest, "expectedDigest");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(cause, "cause");
        RefundPreview preview = previewRefund(trees, skills, snapshot, definition, treeId, nodeId);
        if (!preview.allowed()) {
            throw new IllegalArgumentException(preview.blockers().getFirst());
        }
        if (!preview.digest().equals(expectedDigest)) {
            throw new IllegalArgumentException("Refund preview is stale");
        }
        List<TransactionStep> steps = refundSteps(
                trees.tree(treeId).orElseThrow(), skills, snapshot, preview.affectedNodes()
        );
        TransactionPlan root = new TransactionPlan(
                actor,
                target,
                idempotencyKey,
                snapshot.stateRevision(),
                definition,
                cause,
                "Refund tree node cascade",
                steps.getFirst()
        );
        return new CascadePlan(root, steps.subList(1, steps.size()));
    }

    public static RefundPreview previewRespec(
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation treeId
    ) {
        TreeDefinition tree = trees.tree(treeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tree " + treeId)
        );
        Set<ResourceLocation> owned = ownedNodes(snapshot, tree.id());
        if (owned.isEmpty()) {
            return new RefundPreview(tree.id(), tree.id(), List.of(), Map.of(), "0".repeat(64),
                    List.of("Tree has no owned nodes"));
        }
        ResourceLocation seed = owned.stream().min(ResourceLocation::compareNamespaced).orElseThrow();
        Set<ResourceLocation> closure = refundClosure(tree, owned, owned);
        List<ResourceLocation> order = reverseTopological(tree, closure);
        var totals = refundTotals(snapshot, tree, order);
        var blockers = refundBlockers(skills, snapshot, totals);
        return new RefundPreview(
                tree.id(), tree.id(), order, totals,
                refundDigest(tree.id(), seed, order, snapshot, definition), blockers
        );
    }

    public static CascadePlan respec(
            UUID actor,
            UUID target,
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            ResourceLocation treeId,
            String expectedDigest,
            IdempotencyKey idempotencyKey,
            ProgressionCause cause
    ) {
        RefundPreview preview = previewRespec(trees, skills, snapshot, definition, treeId);
        if (!preview.allowed()) {
            throw new IllegalArgumentException(preview.blockers().getFirst());
        }
        if (!preview.digest().equals(expectedDigest)) {
            throw new IllegalArgumentException("Tree respec preview is stale");
        }
        List<TransactionStep> steps = refundSteps(
                trees.tree(treeId).orElseThrow(), skills, snapshot, preview.affectedNodes()
        );
        return new CascadePlan(new TransactionPlan(
                actor,
                target,
                idempotencyKey,
                snapshot.stateRevision(),
                definition,
                cause,
                "Respec tree",
                steps.getFirst()
        ), steps.subList(1, steps.size()));
    }

    public static Optional<CascadePlan> reconcile(
            UUID target,
            TreeCatalog trees,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definition, "definition");
        var suspendedInstances = new TreeSet<PurchaseInstanceId>();
        for (TreeDefinition tree : trees.trees().values()) {
            Set<ResourceLocation> compatible = compatibleOwnedNodes(trees, snapshot, tree);
            if (compatible.isEmpty()) {
                continue;
            }
            Set<ResourceLocation> valid = validOwnedNodes(tree, compatible, snapshot);
            var invalid = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
            invalid.addAll(compatible);
            invalid.removeAll(valid);
            if (!invalid.isEmpty()) {
                Set<ResourceLocation> closure = refundClosure(tree, compatible, invalid);
                List<ResourceLocation> order = reverseTopological(tree, closure);
                Map<ResourceLocation, Long> totals = refundTotals(snapshot, tree, order);
                List<String> blockers = refundBlockers(skills, snapshot, totals);
                if (blockers.isEmpty()) {
                    List<TransactionStep> steps = refundSteps(tree, skills, snapshot, order);
                    return Optional.of(new CascadePlan(new TransactionPlan(
                            target,
                            target,
                            reconcileKey(definition, snapshot, target, "refund"),
                            snapshot.stateRevision(),
                            definition,
                            ProgressionCause.RECONCILE,
                            "Reconcile tree dependencies",
                            steps.getFirst()
                    ), steps.subList(1, steps.size())));
                }
                var revocations = new TreeMap<String, EntitlementMutation>();
                for (ResourceLocation nodeId : order) {
                    PaidCostRecord record = snapshot.paidCosts().get(instance(tree.id(), nodeId));
                    if (record != null) {
                        suspendedInstances.add(record.instanceId());
                        revokeRecordedSources(snapshot, record.persistentSources(), revocations);
                    }
                }
                if (!revocations.isEmpty()) {
                    List<EntitlementMutation> bounded = revocations.values().stream()
                            .limit(TransactionStep.MAX_MUTATIONS).toList();
                    return Optional.of(reconcileGrantPlan(
                            target, snapshot, definition, bounded, List.of()
                    ));
                }
            }
        }

        var authorizedSources = new TreeSet<GrantSourceId>();
        for (PaidCostRecord record : snapshot.paidCosts().values()) {
            if (!record.instanceId().ownerKind().equals(DefinitionKinds.TREE.id())) {
                continue;
            }
            if (suspendedInstances.contains(record.instanceId())) {
                continue;
            }
            Optional<TreeDefinition> tree = trees.tree(record.instanceId().ownerId());
            Optional<TreeNodeDefinition> node = tree.flatMap(value ->
                    trees.node(value.id(), record.instanceId().purchaseId()));
            boolean compatible = tree.isPresent()
                    && node.isPresent()
                    && record.instanceId().rank() == 1
                    && record.ownerLineage().equals(trees.nodeLineageFingerprint(
                    tree.orElseThrow().id(), node.orElseThrow().id()
            ));
            if (!compatible) {
                var mutations = new TreeMap<String, EntitlementMutation>();
                revokeRecordedSources(snapshot, record.persistentSources(), mutations);
                if (!mutations.isEmpty()) {
                    return Optional.of(reconcileGrantPlan(
                            target, snapshot, definition, mutations.values().stream().toList(), List.of()
                    ));
                }
                continue;
            }
            var mutations = new TreeMap<String, EntitlementMutation>();
            var expectedSources = new TreeSet<GrantSourceId>();
            for (TreeAttributeGrant grant : node.orElseThrow().grants()) {
                GrantSourceId source = source(node.orElseThrow(), grant);
                expectedSources.add(source);
                EntitlementKey key = key(grant);
                var desired = new EntitlementContribution(grant.valueUnits(), EntitlementResolver.ADDITIVE);
                var current = snapshot.ownership().getOrDefault(key, Map.of()).get(source);
                if (!desired.equals(current)) {
                    putMutation(mutations, new EntitlementMutation(key, source, Optional.of(desired)));
                }
            }
            authorizedSources.addAll(expectedSources);
            for (GrantSourceId recorded : record.persistentSources()) {
                if (!expectedSources.contains(recorded)) {
                    revokeRecordedSources(snapshot, Set.of(recorded), mutations);
                }
            }
            var paidMutations = new ArrayList<PaidCostMutation>();
            if (!expectedSources.equals(record.persistentSources())) {
                var replacement = new PaidCostRecord(
                        record.instanceId(),
                        record.purchaseTransactionId(),
                        record.definitionRevision(),
                        record.ownerLineage(),
                        record.paidBalances(),
                        expectedSources
                );
                paidMutations.add(PaidCostMutation.replace(record, replacement));
            }
            if (!mutations.isEmpty() || !paidMutations.isEmpty()) {
                return Optional.of(reconcileGrantPlan(
                        target, snapshot, definition, mutations.values().stream().toList(), paidMutations
                ));
            }
        }
        var mutations = new TreeMap<String, EntitlementMutation>();
        snapshot.ownership().forEach((key, owners) -> owners.forEach((source, contribution) -> {
            if (source.ownerKind().equals(DefinitionKinds.TREE.id()) && !authorizedSources.contains(source)) {
                putMutation(mutations, EntitlementMutation.revoke(key, source));
            }
        }));
        if (mutations.isEmpty()) {
            return Optional.empty();
        }
        List<EntitlementMutation> bounded = mutations.values().stream().limit(TransactionStep.MAX_MUTATIONS).toList();
        return Optional.of(reconcileGrantPlan(target, snapshot, definition, bounded, List.of()));
    }

    private static CascadePlan reconcileGrantPlan(
            UUID target,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition,
            List<EntitlementMutation> entitlementMutations,
            List<PaidCostMutation> paidCostMutations
    ) {
        var step = new TransactionStep(
                RECONCILE_ORIGIN, List.of(), entitlementMutations, paidCostMutations, List.of()
        );
        return CascadePlan.single(new TransactionPlan(
                target,
                target,
                reconcileKey(definition, snapshot, target, "grants"),
                snapshot.stateRevision(),
                definition,
                ProgressionCause.RECONCILE,
                "Reconcile tree grants",
                step
        ));
    }

    public static PurchaseInstanceId instance(TreeDefinition tree, TreeNodeDefinition node) {
        return instance(tree.id(), node.id());
    }

    public static PurchaseInstanceId instance(ResourceLocation treeId, ResourceLocation nodeId) {
        return new PurchaseInstanceId(DefinitionKinds.TREE.id(), treeId, nodeId, 1);
    }

    public static GrantSourceId source(TreeNodeDefinition node, TreeAttributeGrant grant) {
        return new GrantSourceId(DefinitionKinds.TREE.id(), node.id(), grant.id());
    }

    private static List<TransactionStep> refundSteps(
            TreeDefinition tree,
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            List<ResourceLocation> order
    ) {
        var result = new ArrayList<TransactionStep>();
        for (ResourceLocation nodeId : order) {
            PaidCostRecord record = snapshot.paidCosts().get(instance(tree.id(), nodeId));
            if (record == null) {
                throw new IllegalArgumentException("Missing historical paid cost for " + nodeId);
            }
            var balances = new ArrayList<BalanceMutation>();
            for (var paid : record.paidBalances().entrySet()) {
                CurrencyDefinition currency = skills.currency(paid.getKey()).orElseThrow(
                        () -> new IllegalArgumentException("Historical currency is unavailable " + paid.getKey())
                );
                balances.add(new BalanceMutation(
                        currency.id(), paid.getValue(), currency.minimum(), currency.maximum()
                ));
            }
            var entitlements = new ArrayList<EntitlementMutation>();
            for (GrantSourceId source : record.persistentSources()) {
                snapshot.ownership().forEach((key, owners) -> {
                    if (owners.containsKey(source)) {
                        entitlements.add(EntitlementMutation.revoke(key, source));
                    }
                });
            }
            result.add(new TransactionStep(
                    REFUND_ORIGIN,
                    balances,
                    entitlements,
                    List.of(PaidCostMutation.remove(record)),
                    List.of()
            ));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Tree refund has no owned nodes");
        }
        return List.copyOf(result);
    }

    private static Set<ResourceLocation> refundClosure(
            TreeDefinition tree,
            Set<ResourceLocation> owned,
            Set<ResourceLocation> seeds
    ) {
        var closure = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        closure.addAll(seeds);
        boolean changed;
        do {
            changed = false;
            for (TreeNodeDefinition candidate : tree.nodes()) {
                if (!owned.contains(candidate.id()) || closure.contains(candidate.id())) {
                    continue;
                }
                boolean lostRequired = candidate.requires().stream().anyMatch(closure::contains);
                boolean lostEveryAlternative = !candidate.requiresAny().isEmpty()
                        && candidate.requiresAny().stream().noneMatch(value ->
                        owned.contains(value) && !closure.contains(value));
                if (lostRequired || lostEveryAlternative) {
                    changed |= closure.add(candidate.id());
                }
            }
        } while (changed);
        return Collections.unmodifiableSet(new LinkedHashSet<>(closure));
    }

    private static List<ResourceLocation> reverseTopological(
            TreeDefinition tree,
            Set<ResourceLocation> closure
    ) {
        var indegree = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        var dependents = new TreeMap<ResourceLocation, List<ResourceLocation>>(ResourceLocation::compareNamespaced);
        for (ResourceLocation nodeId : closure) {
            TreeNodeDefinition node = tree.nodesById().get(nodeId);
            if (node == null) {
                throw new IllegalArgumentException("Refund node is missing from the current tree " + nodeId);
            }
            var dependencies = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
            dependencies.addAll(node.requires());
            dependencies.addAll(node.requiresAny());
            dependencies.retainAll(closure);
            indegree.put(nodeId, dependencies.size());
            for (ResourceLocation dependency : dependencies) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(nodeId);
            }
        }
        var ready = new PriorityQueue<ResourceLocation>(ResourceLocation::compareNamespaced);
        indegree.forEach((node, degree) -> {
            if (degree == 0) {
                ready.add(node);
            }
        });
        var topological = new ArrayList<ResourceLocation>(closure.size());
        while (!ready.isEmpty()) {
            ResourceLocation node = ready.remove();
            topological.add(node);
            for (ResourceLocation dependent : dependents.getOrDefault(node, List.of()).stream()
                    .sorted(ResourceLocation::compareNamespaced).toList()) {
                int next = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                if (next == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (topological.size() != closure.size()) {
            throw new IllegalArgumentException("Refund closure is not acyclic");
        }
        Collections.reverse(topological);
        return List.copyOf(topological);
    }

    private static Map<ResourceLocation, Long> refundTotals(
            ProgressionSnapshot snapshot,
            TreeDefinition tree,
            List<ResourceLocation> order
    ) {
        var totals = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        for (ResourceLocation nodeId : order) {
            PaidCostRecord record = snapshot.paidCosts().get(instance(tree.id(), nodeId));
            if (record == null) {
                throw new IllegalArgumentException("Missing historical paid cost for " + nodeId);
            }
            record.paidBalances().forEach((currency, amount) -> totals.merge(currency, amount, Math::addExact));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(totals));
    }

    private static List<String> refundBlockers(
            SkillCatalog skills,
            ProgressionSnapshot snapshot,
            Map<ResourceLocation, Long> totals
    ) {
        var blockers = new ArrayList<String>();
        totals.forEach((currencyId, amount) -> {
            Optional<CurrencyDefinition> currency = skills.currency(currencyId);
            if (currency.isEmpty()) {
                blockers.add("Historical currency is unavailable " + currencyId);
                return;
            }
            try {
                if (Math.addExact(currentBalance(snapshot, currency.orElseThrow()), amount)
                        > currency.orElseThrow().maximum()) {
                    blockers.add("Refund exceeds the currency maximum for " + currencyId);
                }
            } catch (ArithmeticException exception) {
                blockers.add("Refund overflows currency " + currencyId);
            }
        });
        return List.copyOf(blockers);
    }

    private static Set<ResourceLocation> ownedNodes(ProgressionSnapshot snapshot, ResourceLocation treeId) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.paidCosts().keySet().stream()
                .filter(instance -> instance.ownerKind().equals(DefinitionKinds.TREE.id()))
                .filter(instance -> instance.ownerId().equals(treeId))
                .filter(instance -> instance.rank() == 1)
                .forEach(instance -> result.add(instance.purchaseId()));
        return Collections.unmodifiableSet(new LinkedHashSet<>(result));
    }

    private static Set<ResourceLocation> compatibleOwnedNodes(
            TreeCatalog trees,
            ProgressionSnapshot snapshot,
            TreeDefinition tree
    ) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.paidCosts().values().stream()
                .filter(record -> record.instanceId().ownerKind().equals(DefinitionKinds.TREE.id()))
                .filter(record -> record.instanceId().ownerId().equals(tree.id()))
                .filter(record -> record.instanceId().rank() == 1)
                .filter(record -> tree.nodesById().containsKey(record.instanceId().purchaseId()))
                .filter(record -> record.ownerLineage().equals(trees.nodeLineageFingerprint(
                        tree.id(), record.instanceId().purchaseId()
                )))
                .forEach(record -> result.add(record.instanceId().purchaseId()));
        return result;
    }

    private static Set<ResourceLocation> validOwnedNodes(
            TreeDefinition tree,
            Set<ResourceLocation> compatible,
            ProgressionSnapshot snapshot
    ) {
        var valid = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        if (tree.enabled()) {
            valid.addAll(compatible);
        }
        boolean changed;
        do {
            changed = false;
            for (ResourceLocation nodeId : List.copyOf(valid)) {
                TreeNodeDefinition node = tree.nodesById().get(nodeId);
                boolean levelsPass = node.minimumSkillLevels().entrySet().stream().allMatch(entry ->
                        snapshot.balances().getOrDefault(SkillStateIds.level(entry.getKey()), 0L)
                                >= entry.getValue());
                boolean requiredPass = valid.containsAll(node.requires());
                boolean anyPass = node.requiresAny().isEmpty()
                        || node.requiresAny().stream().anyMatch(valid::contains);
                if (!levelsPass || !requiredPass || !anyPass) {
                    changed |= valid.remove(nodeId);
                }
            }
        } while (changed);
        return valid;
    }

    private static void revokeRecordedSources(
            ProgressionSnapshot snapshot,
            Set<GrantSourceId> sources,
            Map<String, EntitlementMutation> mutations
    ) {
        for (GrantSourceId source : sources) {
            snapshot.ownership().forEach((key, owners) -> {
                if (owners.containsKey(source)) {
                    putMutation(mutations, EntitlementMutation.revoke(key, source));
                }
            });
        }
    }

    private static void putMutation(Map<String, EntitlementMutation> mutations, EntitlementMutation mutation) {
        mutations.put(mutation.key() + "\n" + mutation.source(), mutation);
    }

    private static IdempotencyKey reconcileKey(
            DefinitionRevision definition,
            ProgressionSnapshot snapshot,
            UUID target,
            String kind
    ) {
        return new IdempotencyKey("phase10/reconcile/" + kind + "/" + definition.generation()
                + "/" + snapshot.stateRevision() + "/" + target);
    }

    private static long currentBalance(ProgressionSnapshot snapshot, CurrencyDefinition currency) {
        return snapshot.balances().getOrDefault(currency.id(), currency.initial());
    }

    private static EntitlementKey key(TreeAttributeGrant grant) {
        return new EntitlementKey(grant.operation().targetType(), grant.attribute());
    }

    private static String refundDigest(
            ResourceLocation treeId,
            ResourceLocation selectedNode,
            List<ResourceLocation> order,
            ProgressionSnapshot snapshot,
            DefinitionRevision definition
    ) {
        var text = new StringBuilder();
        text.append(treeId).append('\n').append(selectedNode).append('\n')
                .append(snapshot.stateRevision()).append('\n')
                .append(definition.generation()).append('\n')
                .append(definition.semanticDigest()).append('\n');
        for (ResourceLocation nodeId : order) {
            PaidCostRecord record = snapshot.paidCosts().get(instance(treeId, nodeId));
            if (record == null) {
                text.append("missing ").append(nodeId).append('\n');
                continue;
            }
            text.append(nodeId).append(' ').append(record.purchaseTransactionId()).append(' ')
                    .append(record.ownerLineage()).append('\n');
            record.paidBalances().forEach((currency, amount) ->
                    text.append(currency).append(' ').append(amount).append('\n'));
            record.persistentSources().forEach(source -> text.append(source).append('\n'));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    public record PurchasePreview(
            ResourceLocation treeId,
            ResourceLocation nodeId,
            ResourceLocation currency,
            long cost,
            long balance,
            List<String> blockers
    ) {
        public PurchasePreview {
            Objects.requireNonNull(treeId, "treeId");
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(currency, "currency");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }
    }

    public record RefundPreview(
            ResourceLocation treeId,
            ResourceLocation selectedNode,
            List<ResourceLocation> affectedNodes,
            Map<ResourceLocation, Long> refundBalances,
            String digest,
            List<String> blockers
    ) {
        public RefundPreview {
            Objects.requireNonNull(treeId, "treeId");
            Objects.requireNonNull(selectedNode, "selectedNode");
            affectedNodes = List.copyOf(Objects.requireNonNull(affectedNodes, "affectedNodes"));
            var sortedRefunds = new TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
            sortedRefunds.putAll(Objects.requireNonNull(refundBalances, "refundBalances"));
            refundBalances = Collections.unmodifiableMap(new LinkedHashMap<>(sortedRefunds));
            Objects.requireNonNull(digest, "digest");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }

        public boolean allowed() {
            return blockers.isEmpty();
        }
    }
}
