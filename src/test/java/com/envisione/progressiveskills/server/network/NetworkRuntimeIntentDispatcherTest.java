package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.ServerNetworkSessions;
import com.envisione.progressiveskills.common.network.TreeIntentPayload;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.tree.TreeProgression;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetworkRuntimeIntentDispatcherTest {
    private static final UUID SESSION = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final String SEMANTIC_DIGEST = "a".repeat(64);
    private static final String PREVIEW_DIGEST = "b".repeat(64);
    private static final ResourceLocation TREE = ResourceLocation.parse("test:tree");
    private static final ResourceLocation NODE = ResourceLocation.parse("test:tree/node");
    private static final ResourceLocation DEPENDENT = ResourceLocation.parse("test:tree/dependent");
    private static final ResourceLocation CURRENCY = ResourceLocation.parse("test:points");

    @Test
    void buyUsesTheSessionRequestKeyAndMapsCommittedAndRejectedOutcomes() {
        var operations = new FakeOperations();
        NetworkPayloads.Intent intent = intent(7, NetworkPayloads.IntentType.TREE_BUY,
                TreeIntentPayload.buy(TREE, NODE));

        ServerNetworkSessions.IntentExecution accepted = NetworkRuntime.dispatchTreeIntent(
                intent, TreeIntentPayload.buy(TREE, NODE), operations
        );

        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, accepted.status());
        assertEquals("Tree node purchased", accepted.message());
        assertEquals("phase10/network/" + SESSION + "/7", operations.purchaseKey.value());
        assertEquals(TREE, operations.purchaseTree);
        assertEquals(NODE, operations.purchaseNode);

        operations.purchaseOutcome = new NetworkRuntime.TreeMutationOutcome(false, "Purchase denied");
        ServerNetworkSessions.IntentExecution rejected = NetworkRuntime.dispatchTreeIntent(
                intent, TreeIntentPayload.buy(TREE, NODE), operations
        );

        assertEquals(NetworkPayloads.IntentStatus.INVALID, rejected.status());
        assertEquals("Purchase denied", rejected.message());
    }

    @Test
    void refundPreviewReturnsTheBoundedFollowupWithTheIntentEnvelope() {
        var operations = new FakeOperations();
        operations.preview = new TreeProgression.RefundPreview(
                TREE,
                NODE,
                List.of(NODE, DEPENDENT),
                Map.of(CURRENCY, 3L),
                PREVIEW_DIGEST,
                List.of("Dependent node will also be refunded")
        );
        TreeIntentPayload payload = TreeIntentPayload.refundPreview(TREE, NODE);
        NetworkPayloads.Intent intent = intent(8, NetworkPayloads.IntentType.TREE_REFUND_PREVIEW, payload);

        ServerNetworkSessions.IntentExecution execution = NetworkRuntime.dispatchTreeIntent(
                intent, payload, operations
        );

        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, execution.status());
        assertEquals("Tree refund preview ready", execution.message());
        assertEquals(1, execution.followups().size());
        var followup = (NetworkPayloads.TreeRefundPreview) execution.followups().getFirst();
        assertEquals(SESSION, followup.sessionId());
        assertEquals(8, followup.requestId());
        assertEquals(11, followup.definitionGeneration());
        assertEquals(SEMANTIC_DIGEST, followup.semanticDigest());
        assertEquals(13, followup.stateRevision());
        assertEquals(TREE, followup.treeId());
        assertEquals(NODE, followup.nodeId());
        assertEquals(List.of(NODE, DEPENDENT), followup.affectedNodes());
        assertEquals(Map.of(CURRENCY, 3L), followup.refundBalances());
        assertEquals(PREVIEW_DIGEST, followup.previewDigest());
        assertEquals(List.of("Dependent node will also be refunded"), followup.blockers());
    }

    @Test
    void refundConfirmPassesThePreviewDigestAndMapsBothOutcomes() {
        var operations = new FakeOperations();
        TreeIntentPayload payload = TreeIntentPayload.refundConfirm(TREE, NODE, PREVIEW_DIGEST);
        NetworkPayloads.Intent intent = intent(9, NetworkPayloads.IntentType.TREE_REFUND_CONFIRM, payload);

        ServerNetworkSessions.IntentExecution accepted = NetworkRuntime.dispatchTreeIntent(
                intent, payload, operations
        );

        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, accepted.status());
        assertEquals("Tree refund committed", accepted.message());
        assertEquals(PREVIEW_DIGEST, operations.refundDigest);
        assertEquals("phase10/network/" + SESSION + "/9", operations.refundKey.value());
        assertEquals(TREE, operations.refundTree);
        assertEquals(NODE, operations.refundNode);

        operations.refundOutcome = new NetworkRuntime.TreeMutationOutcome(false, "Refund denied");
        ServerNetworkSessions.IntentExecution rejected = NetworkRuntime.dispatchTreeIntent(
                intent, payload, operations
        );

        assertEquals(NetworkPayloads.IntentStatus.INVALID, rejected.status());
        assertEquals("Refund denied", rejected.message());
    }

    @Test
    void inactiveDataIsRejectedBeforeAnyTreeOperationRuns() {
        var operations = new FakeOperations();
        operations.active = false;
        TreeIntentPayload payload = TreeIntentPayload.buy(TREE, NODE);

        ServerNetworkSessions.IntentExecution execution = NetworkRuntime.dispatchTreeIntent(
                intent(10, NetworkPayloads.IntentType.TREE_BUY, payload), payload, operations
        );

        assertEquals(NetworkPayloads.IntentStatus.INVALID, execution.status());
        assertEquals("Player progression data is quarantined", execution.message());
        assertEquals(0, operations.operationCount);
        assertNull(operations.purchaseKey);
    }

    private static NetworkPayloads.Intent intent(
            long requestId,
            NetworkPayloads.IntentType type,
            TreeIntentPayload payload
    ) {
        return new NetworkPayloads.Intent(
                SESSION,
                requestId,
                11,
                SEMANTIC_DIGEST,
                13,
                type,
                payload.encode(type)
        );
    }

    private static final class FakeOperations implements NetworkRuntime.TreeIntentOperations {
        private boolean active = true;
        private int operationCount;
        private NetworkRuntime.TreeMutationOutcome purchaseOutcome =
                new NetworkRuntime.TreeMutationOutcome(true, "Committed");
        private NetworkRuntime.TreeMutationOutcome refundOutcome =
                new NetworkRuntime.TreeMutationOutcome(true, "Committed");
        private TreeProgression.RefundPreview preview = new TreeProgression.RefundPreview(
                TREE, NODE, List.of(NODE), Map.of(CURRENCY, 1L), PREVIEW_DIGEST, List.of()
        );
        private ResourceLocation purchaseTree;
        private ResourceLocation purchaseNode;
        private IdempotencyKey purchaseKey;
        private ResourceLocation refundTree;
        private ResourceLocation refundNode;
        private String refundDigest;
        private IdempotencyKey refundKey;

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public NetworkRuntime.TreeMutationOutcome purchase(
                ResourceLocation treeId,
                ResourceLocation nodeId,
                IdempotencyKey idempotencyKey
        ) {
            operationCount++;
            purchaseTree = treeId;
            purchaseNode = nodeId;
            purchaseKey = idempotencyKey;
            return purchaseOutcome;
        }

        @Override
        public TreeProgression.RefundPreview previewRefund(
                ResourceLocation treeId,
                ResourceLocation nodeId
        ) {
            operationCount++;
            assertEquals(TREE, treeId);
            assertEquals(NODE, nodeId);
            return preview;
        }

        @Override
        public NetworkRuntime.TreeMutationOutcome refund(
                ResourceLocation treeId,
                ResourceLocation nodeId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        ) {
            operationCount++;
            refundTree = treeId;
            refundNode = nodeId;
            refundDigest = previewDigest;
            refundKey = idempotencyKey;
            return refundOutcome;
        }
    }
}
