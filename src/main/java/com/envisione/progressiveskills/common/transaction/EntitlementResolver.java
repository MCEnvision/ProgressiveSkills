package com.envisione.progressiveskills.common.transaction;

/** Closed deterministic resolver vocabulary for source-owned long values. */
public enum EntitlementResolver {
    ADDITIVE,
    HIGHEST,
    LOWEST,
    BOOLEAN_UNION
}
