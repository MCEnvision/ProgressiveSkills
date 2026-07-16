package com.envisione.progressiveskills.common.transaction;

import java.util.Objects;

/** Physical adapter outcome for one transition action. */
public record ActionExecution(boolean successful, String detail) {
    public ActionExecution {
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty() || detail.length() > 512) {
            throw new IllegalArgumentException("Action execution detail must be bounded and nonblank");
        }
    }

    public static ActionExecution success(String detail) {
        return new ActionExecution(true, detail);
    }

    public static ActionExecution failure(String detail) {
        return new ActionExecution(false, detail);
    }
}
