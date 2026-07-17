package com.envisione.progressiveskills.server.ability;

import com.envisione.progressiveskills.common.ability.AbilityProgression;
import com.envisione.progressiveskills.common.transaction.TransitionAction;

import java.util.ArrayList;
import java.util.Objects;

public final class AbilityActivationBridge {
    private AbilityActivationBridge() {
    }

    public static AbilityProgression.ActivationPlan attach(
            AbilityProgression.ActivationPlan plan,
            AbilityExecutionTarget target
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(target, "target");
        if (plan.targeting().mode() != target.mode()) {
            throw new IllegalArgumentException("Resolved ability target has the wrong mode");
        }
        var transitions = new ArrayList<TransitionAction>(
                plan.vanillaCosts().size() + plan.actions().size()
        );
        plan.vanillaCosts().forEach(cost -> transitions.add(
                AbilityTransitionActions.cost(plan.abilityId(), cost)));
        plan.actions().forEach(action -> transitions.add(
                AbilityTransitionActions.action(plan.abilityId(), action, target)));
        return plan.withTransitionActions(transitions);
    }
}
