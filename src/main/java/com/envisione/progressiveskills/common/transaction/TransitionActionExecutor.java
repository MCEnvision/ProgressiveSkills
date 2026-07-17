package com.envisione.progressiveskills.common.transaction;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** Server adapter that validates and executes typed transition actions. */
public interface TransitionActionExecutor {
    /** Returns a bounded rejection reason before any state commits, or empty when executable. */
    Optional<String> validate(UUID targetId, TransitionAction action);

    default Optional<String> validateAll(UUID targetId, List<TransitionAction> actions) {
        for (TransitionAction action : List.copyOf(actions)) {
            Optional<String> rejection = validate(targetId, action);
            if (rejection.isPresent()) {
                return rejection;
            }
        }
        return Optional.empty();
    }

    /** Executes after state commit; failures are isolated and audited according to the action policy. */
    ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action);
}
