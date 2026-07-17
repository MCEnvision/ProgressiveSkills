package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.pack.AvailableEnvironment;
import com.envisione.progressiveskills.common.pack.ContentPackLoader;
import com.envisione.progressiveskills.common.pack.PackRoot;
import com.envisione.progressiveskills.common.pack.PackRootTier;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.server.pack.StarterPackInstaller;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeProgressionReconciliationTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final ResourceLocation TREE = id("progressiveskills:physique_training");
    private static final ResourceLocation CONDITIONING = id(
            "progressiveskills:physique_training/conditioning"
    );
    private static final ResourceLocation RESILIENCE = id(
            "progressiveskills:physique_training/resilience"
    );
    private static final ResourceLocation POINTS = id("progressiveskills:global_points");
    private static final ResourceLocation PHYSIQUE = id("progressiveskills:physique");

    @TempDir
    Path temporaryDirectory;

    @Test
    void grantValueUpdatePreservesPaidLedgerAndSecondReconcileIsNoOp() throws IOException {
        Path packs = temporaryDirectory.resolve("grant-update-packs");
        Catalogs original = catalogs(packs, 7);
        var service = service(10, 1);
        buy(service, original, CONDITIONING, "grant-update");
        var instance = TreeProgression.instance(TREE, CONDITIONING);
        var paidBefore = service.snapshot(PLAYER).paidCosts().get(instance);

        Path treeFile = treeFile(packs);
        Files.writeString(treeFile, Files.readString(treeFile).replaceFirst("value = 1.0", "value = 2.0"));
        Catalogs edited = catalogsFromExisting(packs, 8);
        var plan = TreeProgression.reconcile(
                PLAYER, edited.trees(), edited.skills(), service.snapshot(PLAYER), edited.revision()
        ).orElseThrow();

        assertTrue(execute(service, plan, edited.revision()).status().committed());
        var after = service.snapshot(PLAYER);
        var node = edited.trees().node(TREE, CONDITIONING).orElseThrow();
        var grant = node.grants().getFirst();
        var key = new EntitlementKey(grant.operation().targetType(), grant.attribute());
        var source = TreeProgression.source(node, grant);
        assertEquals(FixedPoint.parse("2"), after.ownership().get(key).get(source).value());
        assertEquals(paidBefore, after.paidCosts().get(instance));
        assertTrue(TreeProgression.reconcile(
                PLAYER, edited.trees(), edited.skills(), after, edited.revision()
        ).isEmpty());
    }

    @Test
    void invalidDependencyCascadeRefundsExactHistoricalCosts() throws IOException {
        Path packs = temporaryDirectory.resolve("invalid-requirement-packs");
        Catalogs original = catalogs(packs, 7);
        var service = service(20, 2);
        buy(service, original, CONDITIONING, "invalid-conditioning");
        buy(service, original, RESILIENCE, "invalid-resilience");
        assertEquals(17, service.snapshot(PLAYER).balances().get(POINTS));

        Path treeFile = treeFile(packs);
        String editedTree = Files.readString(treeFile)
                .replaceFirst("cost = 1", "cost = 7")
                .replaceFirst("cost = 2", "cost = 8")
                .replace("min_level = { \"progressiveskills:physique\" = 1 }",
                        "min_level = { \"progressiveskills:physique\" = 3 }");
        Files.writeString(treeFile, editedTree);
        Catalogs edited = catalogsFromExisting(packs, 8);
        var plan = TreeProgression.reconcile(
                PLAYER, edited.trees(), edited.skills(), service.snapshot(PLAYER), edited.revision()
        ).orElseThrow();

        assertTrue(execute(service, plan, edited.revision()).status().committed());
        var after = service.snapshot(PLAYER);
        assertEquals(20, after.balances().get(POINTS));
        assertFalse(after.paidCosts().containsKey(TreeProgression.instance(TREE, CONDITIONING)));
        assertFalse(after.paidCosts().containsKey(TreeProgression.instance(TREE, RESILIENCE)));
    }

    @Test
    void blockedRefundRevokesTreeGrantAndRetainsPaidRecord() throws IOException {
        Path packs = temporaryDirectory.resolve("blocked-refund-packs");
        Catalogs original = catalogs(packs, 7);
        var service = service(10, 1);
        buy(service, original, CONDITIONING, "blocked-refund");
        var purchased = service.exportAccount(PLAYER);
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                purchased.stateRevision() + 1,
                merge(purchased.balances(), POINTS, 10),
                purchased.ownership(),
                purchased.paidCosts(),
                purchased.receipts(),
                purchased.idempotencyResults(),
                purchased.auditRecords()
        ));

        Path currencyFile = packs.resolve("progressiveskills-core/currencies/global_points.toml");
        Files.writeString(currencyFile, Files.readString(currencyFile)
                .replace("maximum = 1000000000", "maximum = 10"));
        Path treeFile = treeFile(packs);
        Files.writeString(treeFile, Files.readString(treeFile)
                .replace("min_level = { \"progressiveskills:physique\" = 1 }",
                        "min_level = { \"progressiveskills:physique\" = 2 }"));
        Catalogs edited = catalogsFromExisting(packs, 8);
        var plan = assertDoesNotThrow(() -> TreeProgression.reconcile(
                PLAYER, edited.trees(), edited.skills(), service.snapshot(PLAYER), edited.revision()
        )).orElseThrow();

        assertTrue(execute(service, plan, edited.revision()).status().committed());
        var after = service.snapshot(PLAYER);
        assertEquals(10, after.balances().get(POINTS));
        assertTrue(after.paidCosts().containsKey(TreeProgression.instance(TREE, CONDITIONING)));
        assertTrue(after.ownership().isEmpty());
        assertTrue(TreeProgression.reconcile(
                PLAYER, edited.trees(), edited.skills(), after, edited.revision()
        ).isEmpty());
    }

    @Test
    void orphanTreeSourceRevocationKeepsNonTreeCoowner() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("orphan-source-packs"), 7);
        var node = catalogs.trees().node(TREE, CONDITIONING).orElseThrow();
        var grant = node.grants().getFirst();
        var key = new EntitlementKey(grant.operation().targetType(), grant.attribute());
        var treeSource = TreeProgression.source(node, grant);
        var skillSource = new GrantSourceId(
                DefinitionKinds.SKILL.id(), PHYSIQUE, id("progressiveskills:test/health")
        );
        var treeContribution = new EntitlementContribution(
                grant.valueUnits(), EntitlementResolver.ADDITIVE
        );
        var skillContribution = new EntitlementContribution(
                FixedPoint.parse("3"), EntitlementResolver.ADDITIVE
        );
        var service = new ProgressionTransactionService(8, 64, 64, 64, 64, Clock.systemUTC());
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0,
                Map.of(POINTS, 10L, SkillStateIds.level(PHYSIQUE), 1L),
                Map.of(key, Map.of(treeSource, treeContribution, skillSource, skillContribution)),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        ));
        var plan = TreeProgression.reconcile(
                PLAYER, catalogs.trees(), catalogs.skills(), service.snapshot(PLAYER), catalogs.revision()
        ).orElseThrow();

        assertTrue(execute(service, plan, catalogs.revision()).status().committed());
        var after = service.snapshot(PLAYER);
        assertEquals(Map.of(skillSource, skillContribution), after.ownership().get(key));
        assertEquals(FixedPoint.parse("3"), after.projectedValues().get(key));
    }

    @Test
    void refundRejectsStalePreviewDigest() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("stale-preview-packs"), 7);
        var service = service(10, 1);
        buy(service, catalogs, CONDITIONING, "stale-preview");
        var snapshot = service.snapshot(PLAYER);
        var preview = TreeProgression.previewRefund(
                catalogs.trees(), catalogs.skills(), snapshot, catalogs.revision(), TREE, CONDITIONING
        );
        var advanced = new ProgressionSnapshot(
                snapshot.stateRevision() + 1,
                snapshot.balances(),
                snapshot.ownership(),
                snapshot.paidCosts(),
                snapshot.projectedValues(),
                snapshot.receiptCount(),
                snapshot.idempotencyCount(),
                snapshot.auditCount()
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> TreeProgression.refund(
                PLAYER,
                PLAYER,
                catalogs.trees(),
                catalogs.skills(),
                advanced,
                catalogs.revision(),
                TREE,
                CONDITIONING,
                preview.digest(),
                new IdempotencyKey("phase10/test/stale-preview/refund"),
                ProgressionCause.GAMEPLAY
        ));
        assertEquals("Refund preview is stale", exception.getMessage());
    }

    private static void buy(
            ProgressionTransactionService service,
            Catalogs catalogs,
            ResourceLocation node,
            String key
    ) {
        var plan = TreeProgression.purchase(
                PLAYER,
                PLAYER,
                catalogs.trees(),
                catalogs.skills(),
                service.snapshot(PLAYER),
                catalogs.revision(),
                TREE,
                node,
                new IdempotencyKey("phase10/test/reconcile/buy/" + key),
                ProgressionCause.GAMEPLAY
        );
        assertTrue(execute(service, plan, catalogs.revision()).status().committed());
    }

    private static TransactionResult execute(
            ProgressionTransactionService service,
            CascadePlan plan,
            DefinitionRevision revision
    ) {
        return service.execute(plan, revision, new NoopProjector(), new NoopExecutor());
    }

    private static ProgressionTransactionService service(long points, int level) {
        var service = new ProgressionTransactionService(8, 64, 64, 64, 64, Clock.systemUTC());
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0,
                Map.of(POINTS, points, SkillStateIds.level(PHYSIQUE), (long) level),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        ));
        return service;
    }

    private static Map<ResourceLocation, Long> merge(
            Map<ResourceLocation, Long> source,
            ResourceLocation key,
            long value
    ) {
        var result = new LinkedHashMap<>(source);
        result.put(key, value);
        return result;
    }

    private static Catalogs catalogs(Path packs, long generation) throws IOException {
        StarterPackInstaller.install(packs);
        return catalogsFromExisting(packs, generation);
    }

    private static Catalogs catalogsFromExisting(Path packs, long generation) {
        var result = new ContentPackLoader().stage(
                List.of(new PackRoot(PackRootTier.GLOBAL_CONFIG, "test", packs)),
                AvailableEnvironment.empty()
        );
        assertTrue(result.valid(), () -> result.diagnostics().diagnostics().toString());
        var snapshot = result.snapshot().orElseThrow();
        SkillCatalog skills = SkillCatalog.from(snapshot.canonicalIr());
        return new Catalogs(
                skills,
                TreeCatalog.from(snapshot.canonicalIr(), skills),
                new DefinitionRevision(generation, snapshot.contentDigest())
        );
    }

    private static Path treeFile(Path packs) {
        return packs.resolve("progressiveskills-core/trees/physique_training.toml");
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(
            SkillCatalog skills,
            TreeCatalog trees,
            DefinitionRevision revision
    ) {
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
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            return ActionExecution.success("unused");
        }
    }
}
