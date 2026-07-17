package com.envisione.progressiveskills.common.transaction;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface PersistentStateValidator {
    Optional<String> rejection(UUID targetId, PersistedTransactionState candidate);
}
