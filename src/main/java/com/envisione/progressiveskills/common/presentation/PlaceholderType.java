package com.envisione.progressiveskills.common.presentation;

/**
 * Value shapes accepted by a declared {@link ComponentSpec} placeholder.
 *
 * <p>This is schema information only. Resolving a runtime value into text is a
 * later presentation concern and is deliberately absent from the canonical
 * data model.</p>
 */
public enum PlaceholderType {
    TEXT,
    COMPONENT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    RESOURCE_LOCATION,
    UUID,
    DURATION_TICKS
}
