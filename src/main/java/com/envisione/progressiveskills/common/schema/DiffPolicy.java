package com.envisione.progressiveskills.common.schema;

/** Semantic diff behavior recorded for future staged reloads. */
public enum DiffPolicy {
    REPLACE("replace"),
    MERGE_BY_KEY("merge_by_key"),
    SET("set"),
    ORDERED("ordered");

    private final String metadataName;

    DiffPolicy(String metadataName) {
        this.metadataName = metadataName;
    }

    public String metadataName() {
        return metadataName;
    }
}
