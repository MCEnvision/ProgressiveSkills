package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record TreeIntentPayload(
        ResourceLocation treeId,
        ResourceLocation nodeId,
        Optional<String> previewDigest
) {
    public TreeIntentPayload {
        treeId = StableId.requireValid(treeId);
        nodeId = StableId.requireValid(nodeId);
        previewDigest = Objects.requireNonNull(previewDigest, "previewDigest")
                .map(value -> NetworkLimits.requireDigest(value, "tree preview digest"));
    }

    public static TreeIntentPayload buy(ResourceLocation treeId, ResourceLocation nodeId) {
        return new TreeIntentPayload(treeId, nodeId, Optional.empty());
    }

    public static TreeIntentPayload refundPreview(ResourceLocation treeId, ResourceLocation nodeId) {
        return new TreeIntentPayload(treeId, nodeId, Optional.empty());
    }

    public static TreeIntentPayload refundConfirm(
            ResourceLocation treeId,
            ResourceLocation nodeId,
            String previewDigest
    ) {
        return new TreeIntentPayload(treeId, nodeId, Optional.of(previewDigest));
    }

    public String encode(NetworkPayloads.IntentType intentType) {
        requireShape(intentType);
        return treeId + "\n" + nodeId + "\n" + previewDigest.orElse("");
    }

    public static TreeIntentPayload decode(NetworkPayloads.IntentType intentType, String encoded) {
        Objects.requireNonNull(intentType, "intentType");
        NetworkLimits.requireBoundedText(encoded, NetworkLimits.MAX_INTENT_BYTES, "tree intent payload");
        String[] fields = encoded.split("\n", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("Tree intent payload field count is invalid");
        }
        var result = new TreeIntentPayload(
                StableId.parse(fields[0]),
                StableId.parse(fields[1]),
                fields[2].isEmpty() ? Optional.empty() : Optional.of(fields[2])
        );
        result.requireShape(intentType);
        if (!result.encode(intentType).equals(encoded)) {
            throw new IllegalArgumentException("Tree intent payload is not canonical");
        }
        return result;
    }

    private void requireShape(NetworkPayloads.IntentType intentType) {
        Objects.requireNonNull(intentType, "intentType");
        boolean confirm = intentType == NetworkPayloads.IntentType.TREE_REFUND_CONFIRM;
        boolean treeIntent = intentType == NetworkPayloads.IntentType.TREE_BUY
                || intentType == NetworkPayloads.IntentType.TREE_REFUND_PREVIEW
                || confirm;
        if (!treeIntent || confirm != previewDigest.isPresent()) {
            throw new IllegalArgumentException("Tree intent payload does not match its intent type");
        }
    }
}
