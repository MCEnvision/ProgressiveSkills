package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** One fully expanded root or queued child step inside an atomic cascade plan. */
public record TransactionStep(
        ResourceLocation origin,
        List<BalanceMutation> balanceMutations,
        List<EntitlementMutation> entitlementMutations,
        List<PaidCostMutation> paidCostMutations,
        List<TransitionAction> transitionActions
) {
    public static final int MAX_MUTATIONS = 256;
    public static final int MAX_ACTIONS = 128;

    public TransactionStep {
        origin = StableId.requireValid(origin);
        balanceMutations = List.copyOf(Objects.requireNonNull(balanceMutations, "balanceMutations"));
        entitlementMutations = List.copyOf(Objects.requireNonNull(entitlementMutations, "entitlementMutations"));
        paidCostMutations = List.copyOf(Objects.requireNonNull(paidCostMutations, "paidCostMutations"));
        transitionActions = List.copyOf(Objects.requireNonNull(transitionActions, "transitionActions"));
        if (balanceMutations.size() + entitlementMutations.size() + paidCostMutations.size() > MAX_MUTATIONS) {
            throw new IllegalArgumentException("Transaction step exceeds mutation budget " + MAX_MUTATIONS);
        }
        if (transitionActions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("Transaction step exceeds action budget " + MAX_ACTIONS);
        }
        balanceMutations.forEach(value -> Objects.requireNonNull(value, "balance mutation"));
        entitlementMutations.forEach(value -> Objects.requireNonNull(value, "entitlement mutation"));
        paidCostMutations.forEach(value -> Objects.requireNonNull(value, "paid cost mutation"));
        transitionActions.forEach(value -> Objects.requireNonNull(value, "transition action"));
    }

    public TransactionStep(
            ResourceLocation origin,
            List<BalanceMutation> balanceMutations,
            List<EntitlementMutation> entitlementMutations,
            List<TransitionAction> transitionActions
    ) {
        this(origin, balanceMutations, entitlementMutations, List.of(), transitionActions);
    }

    public static TransactionStep empty(ResourceLocation origin) {
        return new TransactionStep(origin, List.of(), List.of(), List.of(), List.of());
    }
}
