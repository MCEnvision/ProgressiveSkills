package com.envisione.progressiveskills.common.presentation;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable tagged icon specification containing unresolved identifiers only.
 *
 * <p>The first reference is the primary value. A cycling-tag icon carries one
 * tag identity whose resolved contents cycle later; a composite badge carries
 * ordered layer references. The fallback is a same-kind safe asset selected
 * when the requested value cannot be resolved in a later phase.</p>
 */
public record IconSpec(
        IconKind kind,
        List<ResourceLocation> references,
        ResourceLocation fallback,
        ComponentSpec altText,
        Optional<ComponentSpec> narration,
        boolean entityPreviewOptIn
) {
    public static final int MAX_REFERENCES = 16;

    public IconSpec {
        kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(references, "references");
        var checkedReferences = new ArrayList<ResourceLocation>(references.size());
        for (var reference : references) {
            checkedReferences.add(StableId.requireValid(reference));
        }
        references = List.copyOf(checkedReferences);
        fallback = StableId.requireValid(fallback);
        altText = Objects.requireNonNull(altText, "altText");
        narration = Objects.requireNonNull(narration, "narration");

        if (references.size() < kind.minimumReferences() || references.size() > kind.maximumReferences()) {
            throw new IllegalArgumentException(
                    kind.serializedName() + " icon requires " + kind.minimumReferences()
                            + ".." + kind.maximumReferences() + " references"
            );
        }
        if (references.size() > MAX_REFERENCES) {
            throw new IllegalArgumentException("too many icon references: " + references.size());
        }
        if (new HashSet<>(references).size() != references.size()) {
            throw new IllegalArgumentException("icon references must be unique");
        }
        if (kind == IconKind.ENTITY_PREVIEW && !entityPreviewOptIn) {
            throw new IllegalArgumentException("entity previews require explicit opt-in");
        }
        if (kind != IconKind.ENTITY_PREVIEW && entityPreviewOptIn) {
            throw new IllegalArgumentException("entity preview opt-in is valid only for entity previews");
        }
    }

    /** Narration defaults to the required alternative text when not overridden. */
    public ComponentSpec effectiveNarration() {
        return narration.orElse(altText);
    }

    public static IconSpec single(
            IconKind kind,
            ResourceLocation reference,
            ResourceLocation fallback,
            ComponentSpec altText
    ) {
        return new IconSpec(kind, List.of(reference), fallback, altText, Optional.empty(), false);
    }

    public static IconSpec single(
            IconKind kind,
            ResourceLocation reference,
            ResourceLocation fallback,
            ComponentSpec altText,
            ComponentSpec narration
    ) {
        return new IconSpec(
                kind,
                List.of(reference),
                fallback,
                altText,
                Optional.of(Objects.requireNonNull(narration, "narration")),
                false
        );
    }

    public static IconSpec entityPreview(
            ResourceLocation entityType,
            ResourceLocation fallback,
            ComponentSpec altText
    ) {
        return new IconSpec(
                IconKind.ENTITY_PREVIEW,
                List.of(entityType),
                fallback,
                altText,
                Optional.empty(),
                true
        );
    }

    public static IconSpec entityPreview(
            ResourceLocation entityType,
            ResourceLocation fallback,
            ComponentSpec altText,
            ComponentSpec narration
    ) {
        return new IconSpec(
                IconKind.ENTITY_PREVIEW,
                List.of(entityType),
                fallback,
                altText,
                Optional.of(Objects.requireNonNull(narration, "narration")),
                true
        );
    }
}
