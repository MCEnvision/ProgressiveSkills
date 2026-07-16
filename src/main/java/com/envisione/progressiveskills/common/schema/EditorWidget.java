package com.envisione.progressiveskills.common.schema;

/** UI-neutral widget hints consumed by future authoring surfaces. */
public enum EditorWidget {
    CHECKBOX("checkbox"),
    INTEGER("integer"),
    DECIMAL("decimal"),
    SINGLE_LINE("single_line"),
    MULTI_LINE("multi_line"),
    SELECT("select"),
    RESOURCE_LOCATION("resource_location"),
    COMPONENT("component"),
    ICON("icon"),
    KEY_VALUE("key_value"),
    OBJECT("object"),
    LIST("list");

    private final String metadataName;

    EditorWidget(String metadataName) {
        this.metadataName = metadataName;
    }

    public String metadataName() {
        return metadataName;
    }
}
