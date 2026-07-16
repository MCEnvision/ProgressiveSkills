package com.envisione.progressiveskills.common.presentation;

/**
 * Tagged icon forms promised by the canonical presentation vocabulary.
 * References remain unresolved {@code ResourceLocation}s in Phase 2.
 */
public enum IconKind {
    ITEM("item", 1, 1),
    BLOCK("block", 1, 1),
    TEXTURE("texture", 1, 1),
    ATLAS_SPRITE("atlas_sprite", 1, 1),
    PLAYER_HEAD_PROFILE("player_head", 1, 1),
    ENTITY_PREVIEW("entity_preview", 1, 1),
    CYCLING_TAG("cycling_tag", 1, 1),
    COMPOSITE_BADGE("composite_badge", 2, 16);

    private final String serializedName;
    private final int minimumReferences;
    private final int maximumReferences;

    IconKind(String serializedName, int minimumReferences, int maximumReferences) {
        this.serializedName = serializedName;
        this.minimumReferences = minimumReferences;
        this.maximumReferences = maximumReferences;
    }

    public String serializedName() {
        return serializedName;
    }

    public int minimumReferences() {
        return minimumReferences;
    }

    public int maximumReferences() {
        return maximumReferences;
    }
}
