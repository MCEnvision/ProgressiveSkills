package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.common.network.ClassIntentPayload;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.ServerNetworkSessions;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassEntitlementTypes;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetworkRuntimeClassIntentDispatcherTest {
    private static final UUID SESSION = UUID.fromString("22345678-1234-5678-9abc-123456789abc");
    private static final String SEMANTIC_DIGEST = "a".repeat(64);
    private static final String PREVIEW_DIGEST = "b".repeat(64);
    private static final ResourceLocation MAGE = ResourceLocation.parse("test:mage");
    private static final ResourceLocation WARRIOR = ResourceLocation.parse("test:warrior");
    private static final ResourceLocation CURRENCY = ResourceLocation.parse("test:points");

    @Test
    void selectUsesSessionRequestIdentityAndMapsOutcomes() {
        var operations = new FakeOperations();
        ClassIntentPayload payload = ClassIntentPayload.select(MAGE);
        NetworkPayloads.Intent intent = intent(5, NetworkPayloads.IntentType.CLASS_SELECT, payload);

        ServerNetworkSessions.IntentExecution accepted = NetworkRuntime.dispatchClassIntent(
                intent, payload, operations);
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, accepted.status());
        assertEquals("Class selected", accepted.message());
        assertEquals("phase11/network/" + SESSION + "/5", operations.key.value());
        assertEquals(MAGE, operations.classId);

        operations.mutation = new NetworkRuntime.ClassMutationOutcome(false, "Selection denied");
        ServerNetworkSessions.IntentExecution rejected = NetworkRuntime.dispatchClassIntent(
                intent, payload, operations);
        assertEquals(NetworkPayloads.IntentStatus.INVALID, rejected.status());
        assertEquals("Selection denied", rejected.message());
    }

    @Test
    void respecPreviewAndConfirmationPreserveAuthorityFields() {
        var operations = new FakeOperations();
        ClassIntentPayload previewPayload = ClassIntentPayload.respecPreview(MAGE);
        NetworkPayloads.Intent previewIntent = intent(
                6, NetworkPayloads.IntentType.CLASS_RESPEC_PREVIEW, previewPayload);

        ServerNetworkSessions.IntentExecution previewExecution = NetworkRuntime.dispatchClassIntent(
                previewIntent, previewPayload, operations);
        var preview = (NetworkPayloads.ClassChangePreview) previewExecution.followups().getFirst();
        assertEquals(NetworkPayloads.IntentType.CLASS_RESPEC_PREVIEW, preview.intentType());
        assertEquals(MAGE, preview.classId());
        assertEquals(Optional.empty(), preview.replacementClassId());
        assertEquals(List.of(MAGE), preview.affectedClasses());
        assertEquals(Map.of(CURRENCY, 2L), preview.costBalances());
        assertEquals(PREVIEW_DIGEST, preview.previewDigest());

        ClassIntentPayload confirmPayload = ClassIntentPayload.respecConfirm(MAGE, PREVIEW_DIGEST);
        ServerNetworkSessions.IntentExecution confirmed = NetworkRuntime.dispatchClassIntent(
                intent(7, NetworkPayloads.IntentType.CLASS_RESPEC_CONFIRM, confirmPayload),
                confirmPayload, operations);
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, confirmed.status());
        assertEquals("Class respec committed", confirmed.message());
        assertEquals(PREVIEW_DIGEST, operations.digest);
        assertEquals("phase11/network/" + SESSION + "/7", operations.key.value());
    }

    @Test
    void swapPreviewAndConfirmationCarryBothClassIds() {
        var operations = new FakeOperations();
        operations.preview = new NetworkRuntime.ClassPreviewOutcome(
                MAGE, Optional.of(WARRIOR), List.of(MAGE, WARRIOR),
                Map.of(CURRENCY, 7L), PREVIEW_DIGEST, List.of());
        ClassIntentPayload previewPayload = ClassIntentPayload.swapPreview(MAGE, WARRIOR);
        NetworkPayloads.Intent previewIntent = intent(
                8, NetworkPayloads.IntentType.CLASS_SWAP_PREVIEW, previewPayload);

        ServerNetworkSessions.IntentExecution previewExecution = NetworkRuntime.dispatchClassIntent(
                previewIntent, previewPayload, operations);
        var preview = (NetworkPayloads.ClassChangePreview) previewExecution.followups().getFirst();
        assertEquals(Optional.of(WARRIOR), preview.replacementClassId());
        assertEquals(List.of(MAGE, WARRIOR), preview.affectedClasses());

        ClassIntentPayload confirmPayload = ClassIntentPayload.swapConfirm(
                MAGE, WARRIOR, PREVIEW_DIGEST);
        ServerNetworkSessions.IntentExecution confirmed = NetworkRuntime.dispatchClassIntent(
                intent(9, NetworkPayloads.IntentType.CLASS_SWAP_CONFIRM, confirmPayload),
                confirmPayload, operations);
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, confirmed.status());
        assertEquals("Class swap committed", confirmed.message());
        assertEquals(MAGE, operations.classId);
        assertEquals(WARRIOR, operations.replacementClassId);
        assertEquals(PREVIEW_DIGEST, operations.digest);
    }

    @Test
    void inactiveDataRejectsBeforeClassOperations() {
        var operations = new FakeOperations();
        operations.active = false;
        ClassIntentPayload payload = ClassIntentPayload.select(MAGE);

        ServerNetworkSessions.IntentExecution execution = NetworkRuntime.dispatchClassIntent(
                intent(10, NetworkPayloads.IntentType.CLASS_SELECT, payload), payload, operations);
        assertEquals(NetworkPayloads.IntentStatus.INVALID, execution.status());
        assertEquals("Player progression data is quarantined", execution.message());
        assertEquals(0, operations.operationCount);
        assertNull(operations.key);
    }

    @Test
    void missingClassDefinitionProjectsSelectedStateAsSuspended() {
        CanonicalIr ir = CanonicalIr.of(List.of(), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        ClassCatalog classes = ClassCatalog.from(ir, skills, TreeCatalog.from(ir, skills));
        var selectedKey = new EntitlementKey(ClassEntitlementTypes.SELECTED, MAGE);
        var activeKey = new EntitlementKey(ClassEntitlementTypes.ACTIVE, MAGE);
        var selectedSource = new GrantSourceId(
                ClassProgression.SELECTION_OWNER_KIND, MAGE, ResourceLocation.parse("test:mage/selected"));
        var activeSource = new GrantSourceId(
                ClassProgression.ACTIVE_OWNER_KIND, MAGE, ResourceLocation.parse("test:mage/active"));
        var contribution = new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION);
        var snapshot = new ProgressionSnapshot(
                4, Map.of(), Map.of(
                        selectedKey, Map.of(selectedSource, contribution),
                        activeKey, Map.of(activeSource, contribution)
                ), Map.of(), 0, 0, 0
        );

        Map<ResourceLocation, VisiblePlayerState.ClassSelection> visible =
                NetworkRuntime.visibleClasses(snapshot, classes);
        assertEquals(Map.of(MAGE, new VisiblePlayerState.ClassSelection(
                Optional.empty(), 0, VisiblePlayerState.Activity.SUSPENDED)), visible);
    }

    private static NetworkPayloads.Intent intent(
            long requestId,
            NetworkPayloads.IntentType type,
            ClassIntentPayload payload
    ) {
        return new NetworkPayloads.Intent(
                SESSION, requestId, 11, SEMANTIC_DIGEST, 13,
                type, payload.encode(type));
    }

    private static final class FakeOperations implements NetworkRuntime.ClassIntentOperations {
        private boolean active = true;
        private int operationCount;
        private NetworkRuntime.ClassMutationOutcome mutation =
                new NetworkRuntime.ClassMutationOutcome(true, "Committed");
        private NetworkRuntime.ClassPreviewOutcome preview = new NetworkRuntime.ClassPreviewOutcome(
                MAGE, Optional.empty(), List.of(MAGE), Map.of(CURRENCY, 2L),
                PREVIEW_DIGEST, List.of());
        private ResourceLocation classId;
        private ResourceLocation replacementClassId;
        private String digest;
        private IdempotencyKey key;

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public NetworkRuntime.ClassMutationOutcome select(
                ResourceLocation classId,
                IdempotencyKey idempotencyKey
        ) {
            operationCount++;
            this.classId = classId;
            key = idempotencyKey;
            return mutation;
        }

        @Override
        public NetworkRuntime.ClassPreviewOutcome previewRespec(ResourceLocation classId) {
            operationCount++;
            this.classId = classId;
            return preview;
        }

        @Override
        public NetworkRuntime.ClassMutationOutcome respec(
                ResourceLocation classId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        ) {
            operationCount++;
            this.classId = classId;
            digest = previewDigest;
            key = idempotencyKey;
            return mutation;
        }

        @Override
        public NetworkRuntime.ClassPreviewOutcome previewSwap(
                ResourceLocation removedClassId,
                ResourceLocation replacementClassId
        ) {
            operationCount++;
            classId = removedClassId;
            this.replacementClassId = replacementClassId;
            return preview;
        }

        @Override
        public NetworkRuntime.ClassMutationOutcome swap(
                ResourceLocation removedClassId,
                ResourceLocation replacementClassId,
                String previewDigest,
                IdempotencyKey idempotencyKey
        ) {
            operationCount++;
            classId = removedClassId;
            this.replacementClassId = replacementClassId;
            digest = previewDigest;
            key = idempotencyKey;
            return mutation;
        }
    }
}
