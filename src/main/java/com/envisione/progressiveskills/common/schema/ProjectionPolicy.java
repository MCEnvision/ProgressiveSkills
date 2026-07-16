package com.envisione.progressiveskills.common.schema;

/** Whether a schema field may enter a sanitized client definition projection. */
public enum ProjectionPolicy {
    CLIENT_VISIBLE("client_visible"),
    SERVER_ONLY("server_only"),
    REDACT("redact");

    private final String metadataName;

    ProjectionPolicy(String metadataName) {
        this.metadataName = metadataName;
    }

    public String metadataName() {
        return metadataName;
    }
}
