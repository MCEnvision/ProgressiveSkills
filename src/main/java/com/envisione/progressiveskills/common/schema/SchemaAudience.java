package com.envisione.progressiveskills.common.schema;

/** Identifies whether metadata describes pack authoring input or internal IR. */
public enum SchemaAudience {
    AUTHORING("authoring"),
    INTERNAL("internal");

    private final String metadataName;

    SchemaAudience(String metadataName) {
        this.metadataName = metadataName;
    }

    public String metadataName() {
        return metadataName;
    }
}
