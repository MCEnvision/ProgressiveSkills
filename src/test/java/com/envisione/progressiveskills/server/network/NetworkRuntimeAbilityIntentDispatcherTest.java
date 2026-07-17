package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.common.network.AbilityIntentPayload;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.ServerNetworkSessions;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetworkRuntimeAbilityIntentDispatcherTest {
    private static final UUID SESSION = UUID.fromString("32345678-1234-5678-9abc-123456789abc");
    private static final String SEMANTIC = "c".repeat(64);
    private static final ResourceLocation ABILITY = ResourceLocation.parse("test:guard");

    @Test
    void everyAbilityIntentRoutesOnlyItsCanonicalFieldsAndRequestIdentity() {
        var operations = new FakeOperations();
        assertAccepted(4, NetworkPayloads.IntentType.ABILITY_ASSIGN,
                AbilityIntentPayload.assign(ABILITY, 3), operations);
        assertEquals("assign", operations.operation);
        assertEquals(ABILITY, operations.abilityId);
        assertEquals(3, operations.slot);

        assertAccepted(5, NetworkPayloads.IntentType.ABILITY_UNASSIGN,
                AbilityIntentPayload.unassign(3), operations);
        assertEquals("unassign", operations.operation);
        assertAccepted(6, NetworkPayloads.IntentType.ABILITY_SELECT,
                AbilityIntentPayload.select(2), operations);
        assertEquals("select", operations.operation);
        assertAccepted(7, NetworkPayloads.IntentType.ABILITY_TOGGLE,
                AbilityIntentPayload.toggle(ABILITY), operations);
        assertEquals("toggle", operations.operation);
        assertAccepted(8, NetworkPayloads.IntentType.ABILITY_ACTIVATE,
                AbilityIntentPayload.activate(1), operations);
        assertEquals("activate", operations.operation);
        assertEquals("phase12/network/" + SESSION + "/8", operations.key.value());
    }

    @Test
    void dispatcherMapsMutationFailureAndQuarantineBeforeOperations() {
        var operations = new FakeOperations();
        operations.outcome = new NetworkRuntime.AbilityMutationOutcome(false, "Ability is cooling down");
        AbilityIntentPayload payload = AbilityIntentPayload.activate(0);
        ServerNetworkSessions.IntentExecution rejected = NetworkRuntime.dispatchAbilityIntent(
                intent(9, NetworkPayloads.IntentType.ABILITY_ACTIVATE, payload), payload, operations);
        assertEquals(NetworkPayloads.IntentStatus.INVALID, rejected.status());
        assertEquals("Ability is cooling down", rejected.message());

        operations.active = false;
        operations.operation = null;
        ServerNetworkSessions.IntentExecution quarantined = NetworkRuntime.dispatchAbilityIntent(
                intent(10, NetworkPayloads.IntentType.ABILITY_ACTIVATE, payload), payload, operations);
        assertEquals(NetworkPayloads.IntentStatus.INVALID, quarantined.status());
        assertEquals("Player progression data is quarantined", quarantined.message());
        assertNull(operations.operation);
    }

    private static void assertAccepted(
            long requestId,
            NetworkPayloads.IntentType type,
            AbilityIntentPayload payload,
            FakeOperations operations
    ) {
        ServerNetworkSessions.IntentExecution execution = NetworkRuntime.dispatchAbilityIntent(
                intent(requestId, type, payload), payload, operations);
        assertEquals(NetworkPayloads.IntentStatus.ACCEPTED, execution.status());
        assertEquals(switch (type) {
            case ABILITY_ASSIGN -> "Ability assigned";
            case ABILITY_UNASSIGN -> "Ability unassigned";
            case ABILITY_SELECT -> "Ability slot selected";
            case ABILITY_TOGGLE -> "Ability toggled";
            case ABILITY_ACTIVATE -> "Ability activated";
            default -> throw new IllegalArgumentException("Unexpected test intent type");
        } + ". Committed", execution.message());
    }

    private static NetworkPayloads.Intent intent(
            long requestId,
            NetworkPayloads.IntentType type,
            AbilityIntentPayload payload
    ) {
        return new NetworkPayloads.Intent(
                SESSION, requestId, 12, SEMANTIC, 14, type, payload.encode(type));
    }

    private static final class FakeOperations implements NetworkRuntime.AbilityIntentOperations {
        private boolean active = true;
        private NetworkRuntime.AbilityMutationOutcome outcome =
                new NetworkRuntime.AbilityMutationOutcome(true, "Committed");
        private String operation;
        private ResourceLocation abilityId;
        private int slot;
        private IdempotencyKey key;

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public NetworkRuntime.AbilityMutationOutcome assign(
                ResourceLocation abilityId,
                int slot,
                IdempotencyKey idempotencyKey
        ) {
            operation = "assign";
            this.abilityId = abilityId;
            this.slot = slot;
            key = idempotencyKey;
            return outcome;
        }

        @Override
        public NetworkRuntime.AbilityMutationOutcome unassign(int slot, IdempotencyKey idempotencyKey) {
            operation = "unassign";
            this.slot = slot;
            key = idempotencyKey;
            return outcome;
        }

        @Override
        public NetworkRuntime.AbilityMutationOutcome select(int slot, IdempotencyKey idempotencyKey) {
            operation = "select";
            this.slot = slot;
            key = idempotencyKey;
            return outcome;
        }

        @Override
        public NetworkRuntime.AbilityMutationOutcome toggle(
                ResourceLocation abilityId,
                IdempotencyKey idempotencyKey
        ) {
            operation = "toggle";
            this.abilityId = abilityId;
            key = idempotencyKey;
            return outcome;
        }

        @Override
        public NetworkRuntime.AbilityMutationOutcome activate(int slot, IdempotencyKey idempotencyKey) {
            operation = "activate";
            this.slot = slot;
            key = idempotencyKey;
            return outcome;
        }
    }
}
