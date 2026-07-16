package com.envisione.progressiveskills.common.pack;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** One immutable, schema-neutral patch instruction applied before typed compilation. */
public record DefinitionPatch(
        PatchOperation operation,
        String path,
        Optional<Object> value,
        Optional<String> targetId
) {
    private static final Pattern PATH = Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");

    public DefinitionPatch {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(path, "path");
        if (!PATH.matcher(path).matches() || path.length() > 512) {
            throw new IllegalArgumentException("Invalid patch path: " + path);
        }
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(targetId, "targetId");
        targetId.ifPresent(com.envisione.progressiveskills.common.id.StableId::parse);
        switch (operation) {
            case REMOVE -> {
                if (value.isPresent() || targetId.isPresent()) {
                    throw new IllegalArgumentException("remove patch accepts neither value nor target_id");
                }
            }
            case REPLACE_BY_ID -> {
                if (value.isEmpty() || targetId.isEmpty()) {
                    throw new IllegalArgumentException("replace_by_id requires value and target_id");
                }
            }
            default -> {
                if (value.isEmpty() || targetId.isPresent()) {
                    throw new IllegalArgumentException(operation.name().toLowerCase(java.util.Locale.ROOT)
                            + " requires value and does not accept target_id");
                }
            }
        }
    }
}
