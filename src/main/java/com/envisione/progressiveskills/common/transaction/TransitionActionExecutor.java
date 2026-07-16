package com.envisione.progressiveskills.common.transaction;

import java.util.Optional;
import java.util.UUID;

/** Server adapter that validates and executes typed transition actions. */
public interface TransitionActionExecutor {
    /** Returns a bounded rejection reason before any state commits, or empty when executable. */
    Optional<String> validate(UUID targetId, TransitionAction action);

    /** Executes after state commit; failures are isolated and audited according to the action policy. */
    ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action);
}
