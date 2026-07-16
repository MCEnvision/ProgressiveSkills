package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;

/** Auditable result paired with the exact planned transition action. */
public record TransitionActionResult(
        TransitionAction action,
        ActionDisposition disposition,
        String detail
) {
    public TransitionActionResult {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(disposition, "disposition");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
