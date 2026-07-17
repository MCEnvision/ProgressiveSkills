package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.data.NbtDataLimits;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsDataSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionPersistenceCapacityTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000051");
    private static final DefinitionRevision DEFINITIONS = new DefinitionRevision(10, "a".repeat(64));
    private static final ResourceLocation ORIGIN = id("progressiveskills:capacity");
    private static final ResourceLocation POINTS = id("progressiveskills:points");
    private static final PersistentProjector PROJECTOR = new PersistentProjector() {
        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return Optional.empty();
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
        }
    };
    private static final TransitionActionExecutor EXECUTOR = new TransitionActionExecutor() {
        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            return ActionExecution.success("Delivered");
        }
    };

    @Test
    void defaultPaidLedgerMaximumCommitsAndRoundTripsWithinNbtLimits() {
        ProgressionTransactionService service = defaultService();
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);

        for (int rank = 1; rank <= ProgressionTransactionService.DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT; rank++) {
            PaidCostRecord record = paidRecord(rank);
            CascadePlan plan = plan(
                    "capacity/paid/" + rank,
                    rank - 1L,
                    List.of(),
                    List.of(PaidCostMutation.insert(record)),
                    List.of()
            );
            TransactionResult result = execute(service, data, plan);
            assertTrue(result.status().committed(), "Paid record " + rank + " was rejected. " + result);
            data.replaceTransactionState(service.exportAccount(PLAYER), DEFINITIONS);
        }

        int overflowRank = ProgressionTransactionService.DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT + 1;
        TransactionResult overflow = execute(service, data, plan(
                "capacity/paid/overflow",
                ProgressionTransactionService.DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT,
                List.of(),
                List.of(PaidCostMutation.insert(paidRecord(overflowRank))),
                List.of()
        ));
        assertEquals(ProgressionTransactionService.LEDGER_FULL, overflow.diagnosticCode());
        assertEquals(ProgressionTransactionService.DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT,
                service.snapshot(PLAYER).paidCosts().size());
        assertEquals(ProgressionTransactionService.DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT,
                service.snapshot(PLAYER).stateRevision());

        data.replaceTransactionState(service.exportAccount(PLAYER), DEFINITIONS);
        CompoundTag encoded = ProgressiveSkillsDataSerializer.encode(data);
        assertTrue(NbtDataLimits.rejection(encoded).isEmpty());
        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, encoded);
        assertTrue(decoded.active());
        assertEquals(service.exportAccount(PLAYER), decoded.transactionState());
    }

    @Test
    void progressionContinuesBeyondReplayWindowWhileExactReceiptsRemainDurable() {
        ProgressionTransactionService service = defaultService();
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        TransitionAction permanentReward = new TransitionAction(
                id("progressiveskills:item"),
                new GrantSourceId(
                        id("progressiveskills:skill"),
                        id("progressiveskills:mining"),
                        id("progressiveskills:starter_reward")
                ),
                "minecraft:iron_pickaxe",
                1,
                RepeatPolicy.ONCE_PER_CHARACTER,
                DeliveryContract.EFFECTIVELY_ONCE,
                TransitionFailurePolicy.STOP
        );
        TransactionResult seed = execute(service, data, plan(
                "capacity/receipt",
                0,
                List.of(),
                List.of(),
                List.of(permanentReward)
        ));
        assertTrue(seed.status().committed());
        data.replaceTransactionState(service.exportAccount(PLAYER), DEFINITIONS);

        int awards = ProgressionTransactionService.DEFAULT_MAX_IDEMPOTENCY_RESULTS_PER_ACCOUNT + 32;
        CascadePlan lastPlan = null;
        for (int award = 1; award <= awards; award++) {
            lastPlan = plan(
                    "capacity/award/" + award,
                    award,
                    List.of(new BalanceMutation(POINTS, 1, 0, Long.MAX_VALUE)),
                    List.of(),
                    List.of()
            );
            TransactionResult result = execute(service, data, lastPlan);
            assertTrue(result.status().committed(), "Award " + award + " was rejected. " + result);
            data.replaceTransactionState(service.exportAccount(PLAYER), DEFINITIONS);
        }

        TransactionResult replay = execute(service, data, lastPlan);
        assertTrue(replay.replayed());
        ProgressionSnapshot snapshot = service.snapshot(PLAYER);
        assertEquals(awards + 1L, snapshot.stateRevision());
        assertEquals((long) awards, snapshot.balances().get(POINTS));
        assertEquals(1, snapshot.receiptCount());
        assertTrue(snapshot.idempotencyCount()
                <= ProgressionTransactionService.DEFAULT_MAX_IDEMPOTENCY_RESULTS_PER_ACCOUNT);

        CompoundTag encoded = ProgressiveSkillsDataSerializer.encode(data);
        assertTrue(NbtDataLimits.rejection(encoded).isEmpty());
        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, encoded);
        assertEquals(1, decoded.transactionState().receipts().size());
        assertEquals((long) awards, decoded.transactionState().balances().get(POINTS));
    }

    @Test
    void attachmentPressureRejectsBeforeProjectionOrServiceMutation() {
        ProgressiveSkillsData data = dataAtEncodedCeiling();
        ProgressionTransactionService service = defaultService();
        service.restoreAccount(PLAYER, data.transactionState());
        AtomicInteger applications = new AtomicInteger();
        PersistentProjector projector = new PersistentProjector() {
            @Override
            public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
                return Optional.empty();
            }

            @Override
            public void apply(UUID targetId, List<ProjectionChange> changes) {
                applications.incrementAndGet();
            }
        };
        CascadePlan plan = plan(
                "capacity/extension/pressure",
                0,
                List.of(new BalanceMutation(POINTS, 1, 0, Long.MAX_VALUE)),
                List.of(),
                List.of()
        );

        TransactionResult result = service.executeLoaded(
                plan,
                DEFINITIONS,
                projector,
                EXECUTOR,
                (targetId, candidate) -> ProgressiveSkillsDataSerializer.replacementRejection(
                        data, candidate, DEFINITIONS
                )
        );

        assertEquals(ProgressionTransactionService.LEDGER_FULL, result.diagnosticCode());
        assertEquals(0, applications.get());
        assertEquals(0, service.snapshot(PLAYER).stateRevision());
        assertTrue(service.snapshot(PLAYER).balances().isEmpty());
        assertEquals(0, service.snapshot(PLAYER).idempotencyCount());
        assertTrue(service.audit(PLAYER).isEmpty());
        assertEquals(PersistedTransactionState.empty(), data.transactionState());
        assertTrue(NbtDataLimits.rejection(ProgressiveSkillsDataSerializer.encode(data)).isEmpty());
    }

    @Test
    void failedRestoreCannotCreateOrPersistAReplacementAccount() {
        ProgressionTransactionService source = defaultService();
        ProgressiveSkillsData data = ProgressiveSkillsData.empty(PLAYER);
        for (int rank = 1; rank <= 2; rank++) {
            TransactionResult result = execute(source, data, plan(
                    "capacity/restore/source/" + rank,
                    rank - 1L,
                    List.of(),
                    List.of(PaidCostMutation.insert(paidRecord(rank))),
                    List.of()
            ));
            assertTrue(result.status().committed());
            data.replaceTransactionState(source.exportAccount(PLAYER), DEFINITIONS);
        }
        PersistedTransactionState original = data.transactionState();
        ProgressionTransactionService limited = new ProgressionTransactionService(
                1,
                ProgressionTransactionService.DEFAULT_MAX_RECEIPTS_PER_ACCOUNT,
                ProgressionTransactionService.DEFAULT_MAX_IDEMPOTENCY_RESULTS_PER_ACCOUNT,
                ProgressionTransactionService.DEFAULT_MAX_AUDIT_RECORDS_PER_ACCOUNT,
                1,
                Clock.systemUTC()
        );

        assertThrows(IllegalArgumentException.class, () -> limited.restoreAccount(PLAYER, original));
        assertFalse(limited.hasAccount(PLAYER));
        TransactionResult unavailable = limited.executeLoaded(
                plan(
                        "capacity/restore/rejected",
                        original.stateRevision(),
                        List.of(new BalanceMutation(POINTS, 1, 0, Long.MAX_VALUE)),
                        List.of(),
                        List.of()
                ),
                DEFINITIONS,
                PROJECTOR,
                EXECUTOR,
                (targetId, candidate) -> ProgressiveSkillsDataSerializer.replacementRejection(
                        data, candidate, DEFINITIONS
                )
        );

        assertEquals(ProgressionTransactionService.ACCOUNT_UNAVAILABLE, unavailable.diagnosticCode());
        assertFalse(limited.hasAccount(PLAYER));
        limited.loadedAccount(PLAYER).ifPresent(state -> data.replaceTransactionState(state, DEFINITIONS));
        assertEquals(original, data.transactionState());
        assertEquals(original, ProgressiveSkillsDataSerializer.decode(
                PLAYER, ProgressiveSkillsDataSerializer.encode(data)
        ).transactionState());
    }

    private static ProgressionTransactionService defaultService() {
        return new ProgressionTransactionService(
                1,
                ProgressionTransactionService.DEFAULT_MAX_RECEIPTS_PER_ACCOUNT,
                ProgressionTransactionService.DEFAULT_MAX_IDEMPOTENCY_RESULTS_PER_ACCOUNT,
                ProgressionTransactionService.DEFAULT_MAX_AUDIT_RECORDS_PER_ACCOUNT,
                ProgressionTransactionService.DEFAULT_MAX_PAID_COSTS_PER_ACCOUNT,
                Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static ProgressiveSkillsData dataAtEncodedCeiling() {
        CompoundTag best = null;
        int minimum = 0;
        int maximum = NbtDataLimits.MAX_ARRAY_ELEMENTS;
        while (minimum <= maximum) {
            int candidateLength = minimum + (maximum - minimum) / 2;
            CompoundTag candidate = ProgressiveSkillsDataSerializer.encode(ProgressiveSkillsData.empty(PLAYER));
            CompoundTag extensions = new CompoundTag();
            for (int index = 0; index < 7; index++) {
                extensions.putByteArray("full_" + index, new byte[NbtDataLimits.MAX_ARRAY_ELEMENTS]);
            }
            extensions.putByteArray("remainder", new byte[candidateLength]);
            candidate.put("extensions", extensions);
            if (NbtDataLimits.rejection(candidate).isEmpty()) {
                best = candidate;
                minimum = candidateLength + 1;
            } else {
                maximum = candidateLength - 1;
            }
        }
        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(PLAYER, best);
        assertTrue(decoded.active());
        return decoded;
    }

    private static TransactionResult execute(
            ProgressionTransactionService service,
            ProgressiveSkillsData data,
            CascadePlan plan
    ) {
        return service.execute(
                plan,
                DEFINITIONS,
                PROJECTOR,
                EXECUTOR,
                (targetId, candidate) -> ProgressiveSkillsDataSerializer.replacementRejection(
                        data, candidate, DEFINITIONS
                )
        );
    }

    private static CascadePlan plan(
            String key,
            long expectedRevision,
            List<BalanceMutation> balances,
            List<PaidCostMutation> paidCosts,
            List<TransitionAction> actions
    ) {
        var step = new TransactionStep(ORIGIN, balances, List.of(), paidCosts, actions);
        return CascadePlan.single(new TransactionPlan(
                PLAYER,
                PLAYER,
                new IdempotencyKey(key),
                expectedRevision,
                DEFINITIONS,
                ProgressionCause.GAMEPLAY,
                "Persistence capacity regression",
                step
        ));
    }

    private static PaidCostRecord paidRecord(int rank) {
        PurchaseInstanceId instanceId = new PurchaseInstanceId(
                id("progressiveskills:tree"),
                id("progressiveskills:mining"),
                id("progressiveskills:node"),
                rank
        );
        return new PaidCostRecord(
                instanceId,
                TransactionId.derive(PLAYER, new IdempotencyKey("capacity/source/" + rank)),
                DEFINITIONS,
                "b".repeat(64),
                Map.of(POINTS, 1L),
                Set.of()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
