package com.envisione.progressiveskills.common.data;

/** Retention stage for the bounded raw state captured before a schema migration. */
public enum MigrationShadowStatus {
    FRESH,
    PERSISTED_AFTER_LOGIN
}
