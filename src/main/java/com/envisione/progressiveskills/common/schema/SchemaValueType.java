package com.envisione.progressiveskills.common.schema;

/** Canonical authoring/IR value shapes understood by schema metadata. */
public enum SchemaValueType {
    BOOLEAN("boolean"),
    INTEGER("integer"),
    DECIMAL("decimal"),
    STRING("string"),
    ENUM("enum"),
    RESOURCE_LOCATION("resource_location"),
    COMPONENT("component"),
    ICON("icon"),
    REFERENCE("reference"),
    OBJECT("object"),
    LIST("list"),
    MAP("map");

    private final String authoringName;

    SchemaValueType(String authoringName) {
        this.authoringName = authoringName;
    }

    public String authoringName() {
        return authoringName;
    }
}
