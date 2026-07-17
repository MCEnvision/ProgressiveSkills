package com.envisione.progressiveskills.common.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, fully expanded transaction closure validated before the first mutation. */
public record CascadePlan(TransactionPlan transaction, List<TransactionStep> queuedChildren) {
    public static final int MAX_STEPS = 64;
    public static final int MAX_TOTAL_MUTATIONS = 512;
    public static final int MAX_TOTAL_ACTIONS = 256;

    public CascadePlan {
        Objects.requireNonNull(transaction, "transaction");
        queuedChildren = List.copyOf(Objects.requireNonNull(queuedChildren, "queuedChildren"));
        queuedChildren.forEach(value -> Objects.requireNonNull(value, "queued child"));
        if (queuedChildren.size() + 1 > MAX_STEPS) {
            throw new IllegalArgumentException("Cascade exceeds step budget " + MAX_STEPS);
        }
        long mutations = allSteps(transaction, queuedChildren).stream()
                .mapToLong(step -> (long) step.balanceMutations().size()
                        + step.entitlementMutations().size()
                        + step.paidCostMutations().size())
                .sum();
        long actions = allSteps(transaction, queuedChildren).stream()
                .mapToLong(step -> step.transitionActions().size())
                .sum();
        if (mutations > MAX_TOTAL_MUTATIONS) {
            throw new IllegalArgumentException("Cascade exceeds mutation budget " + MAX_TOTAL_MUTATIONS);
        }
        if (actions > MAX_TOTAL_ACTIONS) {
            throw new IllegalArgumentException("Cascade exceeds action budget " + MAX_TOTAL_ACTIONS);
        }
    }

    public static CascadePlan single(TransactionPlan transaction) {
        return new CascadePlan(transaction, List.of());
    }

    public List<TransactionStep> steps() {
        return allSteps(transaction, queuedChildren);
    }

    private static List<TransactionStep> allSteps(
            TransactionPlan transaction,
            List<TransactionStep> queuedChildren
    ) {
        var result = new ArrayList<TransactionStep>(queuedChildren.size() + 1);
        result.add(transaction.rootStep());
        result.addAll(queuedChildren);
        return List.copyOf(result);
    }
}
