package com.envisione.progressiveskills.common.transaction;

/** Whether later actions remain eligible after one post-commit delivery failure. */
public enum TransitionFailurePolicy {
    CONTINUE,
    STOP
}
