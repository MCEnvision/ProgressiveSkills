package com.envisione.progressiveskills.common.schema;

/** Declarative cross-field relationship understood by future schema consumers. */
public enum SchemaConstraintKind {
    REQUIRED_TOGETHER("required_together"),
    EXACTLY_ONE("exactly_one"),
    REQUIRES("requires");

    private final String metadataName;

    SchemaConstraintKind(String metadataName) {
        this.metadataName = metadataName;
    }

    public String metadataName() {
        return metadataName;
    }
}
