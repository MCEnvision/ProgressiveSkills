package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillCanonicalCodec;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.minecraft.resources.ResourceLocation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeProgressionPropertyTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final ResourceLocation CURRENCY = id("progressiveskills:property_points");
    private static final ResourceLocation TREE = id("progressiveskills:property_tree");
    private static final ResourceLocation FIRST = id("progressiveskills:property_tree/first");
    private static final ResourceLocation SECOND = id("progressiveskills:property_tree/second");
    private static final DefinitionRevision REVISION = new DefinitionRevision(10, "a".repeat(64));

    @Property(tries = 500)
    void purchasesAndRefundsConserveExactPaidCurrency(
            @ForAll @IntRange(min = 2, max = 1_000_000) int startingBalance,
            @ForAll int firstCostSeed,
            @ForAll int secondCostSeed,
            @ForAll boolean reversePurchases,
            @ForAll boolean reverseRefunds
    ) {
        long firstCost = 1L + Math.floorMod((long) firstCostSeed, startingBalance - 1L);
        long secondCost = 1L + Math.floorMod((long) secondCostSeed, startingBalance - firstCost);
        Catalogs catalogs = catalogs(firstCost, secondCost);
        ProgressionTransactionService service = service(startingBalance);
        List<ResourceLocation> purchaseOrder = order(reversePurchases);

        for (int index = 0; index < purchaseOrder.size(); index++) {
            ResourceLocation node = purchaseOrder.get(index);
            var snapshot = service.snapshot(PLAYER);
            var preview = TreeProgression.previewPurchase(
                    catalogs.trees(), catalogs.skills(), snapshot, TREE, node
            );
            assertTrue(preview.allowed());
            var result = service.execute(
                    TreeProgression.purchase(
                            PLAYER,
                            PLAYER,
                            catalogs.trees(),
                            catalogs.skills(),
                            snapshot,
                            REVISION,
                            TREE,
                            node,
                            new IdempotencyKey("phase10/property/buy/" + index),
                            ProgressionCause.GAMEPLAY
                    ),
                    REVISION,
                    new NoopProjector(),
                    new NoopExecutor()
            );
            assertTrue(result.status().committed());
            assertConserved(service.snapshot(PLAYER), startingBalance);
        }

        List<ResourceLocation> refundOrder = order(reverseRefunds);
        for (int index = 0; index < refundOrder.size(); index++) {
            ResourceLocation node = refundOrder.get(index);
            var snapshot = service.snapshot(PLAYER);
            var preview = TreeProgression.previewRefund(
                    catalogs.trees(), catalogs.skills(), snapshot, REVISION, TREE, node
            );
            assertTrue(preview.allowed());
            var result = service.execute(
                    TreeProgression.refund(
                            PLAYER,
                            PLAYER,
                            catalogs.trees(),
                            catalogs.skills(),
                            snapshot,
                            REVISION,
                            TREE,
                            node,
                            preview.digest(),
                            new IdempotencyKey("phase10/property/refund/" + index),
                            ProgressionCause.GAMEPLAY
                    ),
                    REVISION,
                    new NoopProjector(),
                    new NoopExecutor()
            );
            assertTrue(result.status().committed());
            assertConserved(service.snapshot(PLAYER), startingBalance);
        }

        ProgressionSnapshot finalSnapshot = service.snapshot(PLAYER);
        assertEquals((long) startingBalance, finalSnapshot.balances().get(CURRENCY));
        assertTrue(finalSnapshot.paidCosts().isEmpty());
    }

    private static List<ResourceLocation> order(boolean reverse) {
        var nodes = new ArrayList<>(List.of(FIRST, SECOND));
        if (reverse) {
            Collections.reverse(nodes);
        }
        return nodes;
    }

    private static void assertConserved(ProgressionSnapshot snapshot, long startingBalance) {
        long paid = snapshot.paidCosts().values().stream()
                .flatMap(record -> record.paidBalances().values().stream())
                .reduce(0L, Math::addExact);
        assertEquals(startingBalance, Math.addExact(snapshot.balances().get(CURRENCY), paid));
    }

    private static Catalogs catalogs(long firstCost, long secondCost) {
        DefinitionPresentation presentation = presentation();
        CurrencyDefinition currency = new CurrencyDefinition(
                CURRENCY, presentation, 0, 1_000_000, 0, "character"
        );
        TreeDefinition tree = new TreeDefinition(
                TREE,
                presentation,
                true,
                TreeScope.GLOBAL,
                Optional.empty(),
                CURRENCY,
                TreeDependencyPolicy.CASCADE_REFUND,
                List.of(
                        node(FIRST, firstCost, 0),
                        node(SECOND, secondCost, 1)
                )
        );
        Provenance provenance = new Provenance(id("progressiveskills:property_pack"), "memory", "test");
        var ir = CanonicalIr.of(List.of(
                SkillCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.CURRENCY, CURRENCY),
                        currency,
                        provenance,
                        SourceMap.empty()
                ),
                TreeCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.TREE, TREE),
                        tree,
                        provenance,
                        SourceMap.empty()
                )
        ), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        return new Catalogs(skills, TreeCatalog.from(ir, skills));
    }

    private static TreeNodeDefinition node(ResourceLocation id, long cost, int column) {
        return new TreeNodeDefinition(
                id,
                presentation(),
                cost,
                0,
                column,
                List.of(),
                List.of(),
                Map.of(),
                List.of()
        );
    }

    private static DefinitionPresentation presentation() {
        ComponentSpec text = ComponentSpec.literal("Property tree");
        return new DefinitionPresentation(
                text,
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, id("minecraft:stone"), id("minecraft:barrier"), text),
                Set.of()
        );
    }

    private static ProgressionTransactionService service(long startingBalance) {
        var service = new ProgressionTransactionService(8, 64, 64, 64, 64, Clock.systemUTC());
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0,
                Map.of(CURRENCY, startingBalance),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        ));
        return service;
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(SkillCatalog skills, TreeCatalog trees) {
    }

    private static final class NoopProjector implements PersistentProjector {
        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return Optional.empty();
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
        }
    }

    private static final class NoopExecutor implements TransitionActionExecutor {
        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(
                UUID targetId,
                TransactionId transactionId,
                TransitionAction action
        ) {
            return ActionExecution.success("unused");
        }
    }
}
