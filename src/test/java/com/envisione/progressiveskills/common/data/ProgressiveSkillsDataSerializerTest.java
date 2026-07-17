package com.envisione.progressiveskills.common.data;

import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PaidCostMutation;
import com.envisione.progressiveskills.common.transaction.PaidCostRecord;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.PurchaseInstanceId;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionStatus;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveSkillsDataSerializerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final DefinitionRevision DEFINITION = new DefinitionRevision(3, "a".repeat(64));
    private static final ResourceLocation POINTS = id("persistent_points");

    @Test
    void exactTransactionStateSurvivesCodecAndStillReplaysWithoutDelivery() {
        var firstService = service();
        var firstExecutions = new AtomicInteger();
        CascadePlan plan = plan();
        var first = firstService.execute(
                plan,
                DEFINITION,
                projector(),
                executor(firstExecutions)
        );
        assertEquals(TransactionStatus.COMMITTED, first.status());
        assertEquals(1, firstExecutions.get());

        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        data.replaceTransactionState(firstService.exportAccount(PLAYER), DEFINITION);
        CompoundTag encoded = ProgressiveSkillsDataSerializer.encode(data);
        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, encoded);

        assertTrue(decoded.active());
        assertEquals(data.view(), decoded.view());
        var resumedService = service();
        resumedService.restoreAccount(PLAYER, decoded.transactionState());
        var resumedExecutions = new AtomicInteger();
        var replay = resumedService.execute(plan, DEFINITION, projector(), executor(resumedExecutions));

        assertTrue(replay.replayed());
        assertEquals(first.transactionId(), replay.transactionId());
        assertEquals(first.actionResults(), replay.actionResults());
        assertEquals(0, resumedExecutions.get());
        assertEquals(1, resumedService.snapshot(PLAYER).balances().get(POINTS));
        assertEquals(1, resumedService.snapshot(PLAYER).paidCosts().size());
        assertEquals(1, resumedService.audit(PLAYER).getFirst().paidCostMutations().size());
    }

    @Test
    void olderDataMigratesWithShadowAndRetiresItOnlyAfterLoginSaveCycle() {
        CompoundTag old = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        old.putInt("data_version", 1);
        old.putLong("revision", 7);
        old.remove("storage_revision");

        ProgressiveSkillsData migrated = ProgressiveSkillsDataSerializer.decode(PLAYER, old);
        assertTrue(migrated.active());
        assertEquals(7, migrated.view().storageRevision());
        assertEquals(MigrationShadowStatus.FRESH, migrated.view().migrationShadow().orElseThrow().status());

        migrated.onLogin();
        assertEquals(MigrationShadowStatus.PERSISTED_AFTER_LOGIN,
                migrated.viewForSave().migrationShadow().orElseThrow().status());
        assertTrue(migrated.viewForSave().migrationShadow().isEmpty());
    }

    @Test
    void futureCorruptAndOversizedDataQuarantineInsteadOfProjecting() {
        CompoundTag future = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        future.putInt("data_version", 99);
        ProgressiveSkillsData futureData = ProgressiveSkillsDataSerializer.decode(PLAYER, future);
        assertFalse(futureData.active());
        assertEquals(99, futureData.view().quarantine().orElseThrow().sourceVersion());
        assertEquals(99, futureData.view().quarantine().orElseThrow().rawData().getInt("data_version"));

        CompoundTag wrongOwner = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        wrongOwner.putString("player_id", UUID.randomUUID().toString());
        assertFalse(ProgressiveSkillsDataSerializer.decode(PLAYER, wrongOwner).active());

        CompoundTag oversized = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        oversized.putString("unknown_oversized", "x".repeat(NbtDataLimits.MAX_STRING_CHARACTERS + 1));
        ProgressiveSkillsData oversizedData = ProgressiveSkillsDataSerializer.decode(PLAYER, oversized);
        assertFalse(oversizedData.active());
        assertTrue(oversizedData.view().quarantine().orElseThrow().rawData().isEmpty());
    }

    @Test
    void nearCeilingFutureDataFallsBackToDigestOnlyQuarantineThatCanBeSaved() {
        CompoundTag future = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        future.putInt("data_version", 99);
        for (int index = 0; index < 6; index++) {
            future.putByteArray("padding_" + index, new byte[NbtDataLimits.MAX_ARRAY_ELEMENTS]);
        }
        for (int index = 0; index < 100; index++) {
            future.putString("padding_text_" + index, "x".repeat(NbtDataLimits.MAX_STRING_CHARACTERS));
        }
        int low = 0;
        int high = NbtDataLimits.MAX_ARRAY_ELEMENTS;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            future.putByteArray("padding_final", new byte[candidate]);
            if (NbtDataLimits.rejection(future).isEmpty()) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        future.putByteArray("padding_final", new byte[low]);
        assertTrue(NbtDataLimits.rejection(future).isEmpty(),
                () -> NbtDataLimits.rejection(future).orElse("unexpected rejection"));

        ProgressiveSkillsData quarantined = ProgressiveSkillsDataSerializer.decode(PLAYER, future);

        assertFalse(quarantined.active());
        assertTrue(quarantined.view().quarantine().orElseThrow().rawData().isEmpty());
        assertTrue(NbtDataLimits.rejection(ProgressiveSkillsDataSerializer.encode(quarantined)).isEmpty());
    }

    @Test
    void unknownExtensionsSurviveAReadWriteRoundTrip() {
        CompoundTag raw = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
        var extension = new CompoundTag();
        extension.putString("provider_value", "preserved");
        raw.put("future_provider", extension);

        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, raw);
        CompoundTag encoded = ProgressiveSkillsDataSerializer.encode(decoded);
        ProgressiveSkillsData roundTripped = ProgressiveSkillsDataSerializer.decode(PLAYER, encoded);

        assertEquals("preserved", roundTripped.view().unknownExtensions()
                .getCompound("future_provider").getString("provider_value"));
    }

    @Test
    void persistingAnUnchangedTransactionSnapshotDoesNotInventAStorageRevision() {
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        var state = service().exportAccount(PLAYER);

        data.replaceTransactionState(state, DEFINITION);
        long firstRevision = data.view().storageRevision();
        data.replaceTransactionState(state, DEFINITION);

        assertEquals(1, firstRevision);
        assertEquals(firstRevision, data.view().storageRevision());
    }

    @Test
    void deathMarkerAndReceiptCopyIdempotentlyWithTheAttachment() {
        ProgressiveSkillsData original = ProgressiveSkillsData.empty(PLAYER);
        UUID death = UUID.fromString("00000000-0000-0000-0000-000000000599");
        original.prepareDeath(death, Instant.parse("2026-07-16T12:00:00Z"), true);

        ProgressiveSkillsData replacement = original.copyFor(PLAYER);
        assertTrue(replacement.completeDeath(Instant.parse("2026-07-16T12:00:01Z")).isPresent());
        assertTrue(replacement.operationReceipt(death).isPresent());
        assertTrue(replacement.completeDeath(Instant.parse("2026-07-16T12:00:02Z")).isEmpty());
        assertEquals(1, replacement.view().operationReceipts().size());

        ProgressiveSkillsData restarted = ProgressiveSkillsDataSerializer.decode(
                PLAYER,
                ProgressiveSkillsDataSerializer.encode(replacement)
        );
        assertTrue(restarted.operationReceipt(death).isPresent());
        assertTrue(restarted.view().deathMarker().isEmpty());
        assertEquals(replacement.view(), restarted.view());
    }

    private static CascadePlan plan() {
        var key = new IdempotencyKey("persistence/round-trip/" + PLAYER);
        var action = new TransitionAction(
                id("item"),
                new GrantSourceId(id("manual"), id("persistence_test"), id("persistence_test/item")),
                "minecraft:gold_ingot",
                1,
                RepeatPolicy.ONCE_PER_CHARACTER,
                DeliveryContract.EFFECTIVELY_ONCE,
                TransitionFailurePolicy.STOP
        );
        var instance = new PurchaseInstanceId(id("tree"), id("persistence_tree"), id("root"), 1);
        var paid = new PaidCostRecord(
                instance,
                TransactionId.derive(PLAYER, key),
                DEFINITION,
                "b".repeat(64),
                Map.of(POINTS, 1L),
                Set.of(action.source())
        );
        var step = new TransactionStep(
                id("persistence_test"),
                List.of(new BalanceMutation(POINTS, 1, 0, 1)),
                List.of(),
                List.of(PaidCostMutation.insert(paid)),
                List.of(action)
        );
        return CascadePlan.single(new TransactionPlan(
                PLAYER,
                PLAYER,
                key,
                0,
                DEFINITION,
                ProgressionCause.ADMIN,
                "Persistence codec round-trip",
                step
        ));
    }

    private static ProgressionTransactionService service() {
        return new ProgressionTransactionService(
                256,
                512,
                512,
                256,
                Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static com.envisione.progressiveskills.common.transaction.PersistentProjector projector() {
        return new com.envisione.progressiveskills.common.transaction.PersistentProjector() {
            @Override
            public Optional<String> validate(
                    UUID targetId,
                    List<com.envisione.progressiveskills.common.transaction.ProjectionChange> changes
            ) {
                return Optional.empty();
            }

            @Override
            public void apply(
                    UUID targetId,
                    List<com.envisione.progressiveskills.common.transaction.ProjectionChange> changes
            ) {
                // No physical projection is used in this persistence fixture.
            }
        };
    }

    private static TransitionActionExecutor executor(AtomicInteger executions) {
        return new TransitionActionExecutor() {
            @Override
            public Optional<String> validate(UUID targetId, TransitionAction action) {
                return Optional.empty();
            }

            @Override
            public ActionExecution execute(
                    UUID targetId,
                    com.envisione.progressiveskills.common.transaction.TransactionId transactionId,
                    TransitionAction action
            ) {
                executions.incrementAndGet();
                return ActionExecution.success("delivered once");
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
