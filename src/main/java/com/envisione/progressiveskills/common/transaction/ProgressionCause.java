package com.envisione.progressiveskills.common.transaction;

/** Authoritative origin category carried by every progression mutation. */
public enum ProgressionCause {
    GAMEPLAY,
    CHARACTER_CREATION,
    ADMIN,
    OFFLINE_OPERATION,
    MIGRATION,
    RELOAD,
    RECONCILE
}
