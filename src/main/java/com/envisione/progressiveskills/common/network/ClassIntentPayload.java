package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record ClassIntentPayload(
        ResourceLocation classId,
        Optional<ResourceLocation> replacementClassId,
        Optional<String> previewDigest
) {
    public ClassIntentPayload {
        classId = StableId.requireValid(classId);
        replacementClassId = Objects.requireNonNull(replacementClassId, "replacementClassId")
                .map(StableId::requireValid);
        previewDigest = Objects.requireNonNull(previewDigest, "previewDigest")
                .map(value -> NetworkLimits.requireDigest(value, "class preview digest"));
        if (replacementClassId.filter(classId::equals).isPresent()) {
            throw new IllegalArgumentException("Class swap requires two different classes");
        }
    }

    public static ClassIntentPayload select(ResourceLocation classId) {
        return new ClassIntentPayload(classId, Optional.empty(), Optional.empty());
    }

    public static ClassIntentPayload respecPreview(ResourceLocation classId) {
        return new ClassIntentPayload(classId, Optional.empty(), Optional.empty());
    }

    public static ClassIntentPayload respecConfirm(ResourceLocation classId, String previewDigest) {
        return new ClassIntentPayload(classId, Optional.empty(), Optional.of(previewDigest));
    }

    public static ClassIntentPayload swapPreview(
            ResourceLocation removedClassId,
            ResourceLocation replacementClassId
    ) {
        return new ClassIntentPayload(
                removedClassId, Optional.of(replacementClassId), Optional.empty());
    }

    public static ClassIntentPayload swapConfirm(
            ResourceLocation removedClassId,
            ResourceLocation replacementClassId,
            String previewDigest
    ) {
        return new ClassIntentPayload(
                removedClassId, Optional.of(replacementClassId), Optional.of(previewDigest));
    }

    public String encode(NetworkPayloads.IntentType intentType) {
        requireShape(intentType);
        return classId + "\n" + replacementClassId.map(Object::toString).orElse("")
                + "\n" + previewDigest.orElse("");
    }

    public static ClassIntentPayload decode(NetworkPayloads.IntentType intentType, String encoded) {
        Objects.requireNonNull(intentType, "intentType");
        NetworkLimits.requireBoundedText(encoded, NetworkLimits.MAX_INTENT_BYTES, "class intent payload");
        String[] fields = encoded.split("\n", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("Class intent payload field count is invalid");
        }
        var result = new ClassIntentPayload(
                StableId.parse(fields[0]),
                fields[1].isEmpty() ? Optional.empty() : Optional.of(StableId.parse(fields[1])),
                fields[2].isEmpty() ? Optional.empty() : Optional.of(fields[2])
        );
        result.requireShape(intentType);
        if (!result.encode(intentType).equals(encoded)) {
            throw new IllegalArgumentException("Class intent payload is not canonical");
        }
        return result;
    }

    private void requireShape(NetworkPayloads.IntentType intentType) {
        Objects.requireNonNull(intentType, "intentType");
        boolean swap = intentType == NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW
                || intentType == NetworkPayloads.IntentType.CLASS_SWAP_CONFIRM;
        boolean confirm = intentType == NetworkPayloads.IntentType.CLASS_RESPEC_CONFIRM
                || intentType == NetworkPayloads.IntentType.CLASS_SWAP_CONFIRM;
        boolean classIntent = intentType == NetworkPayloads.IntentType.CLASS_SELECT
                || intentType == NetworkPayloads.IntentType.CLASS_RESPEC_PREVIEW
                || intentType == NetworkPayloads.IntentType.CLASS_RESPEC_CONFIRM
                || swap;
        if (!classIntent || swap != replacementClassId.isPresent() || confirm != previewDigest.isPresent()) {
            throw new IllegalArgumentException("Class intent payload does not match its intent type");
        }
    }
}
