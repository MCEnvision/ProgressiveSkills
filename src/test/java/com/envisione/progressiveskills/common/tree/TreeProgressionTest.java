package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsDataSerializer;
import com.envisione.progressiveskills.common.pack.AvailableEnvironment;
import com.envisione.progressiveskills.common.pack.ContentPackLoader;
import com.envisione.progressiveskills.common.pack.PackRoot;
import com.envisione.progressiveskills.common.pack.PackRootTier;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.server.pack.StarterPackInstaller;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeProgressionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final ResourceLocation TREE = id("progressiveskills:physique_training");
    private static final ResourceLocation CONDITIONING = id(
            "progressiveskills:physique_training/conditioning"
    );
    private static final ResourceLocation TECHNIQUE = id(
            "progressiveskills:physique_training/technique"
    );
    private static final ResourceLocation RESILIENCE = id(
            "progressiveskills:physique_training/resilience"
    );
    private static final ResourceLocation MOMENTUM = id(
            "progressiveskills:physique_training/momentum"
    );
    private static final ResourceLocation POINTS = id("progressiveskills:global_points");
    private static final ResourceLocation PHYSIQUE = id("progressiveskills:physique");

    @TempDir
    Path temporaryDirectory;

    @Test
    void purchaseDebitsAndRecordsExactAtomicOwnership() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("packs"), 7);
        var service = service(catalogs, 10, 3);
        var snapshot = service.snapshot(PLAYER);
        var preview = TreeProgression.previewPurchase(
                catalogs.trees(), catalogs.skills(), snapshot, TREE, CONDITIONING
        );
        assertTrue(preview.allowed());

        var plan = TreeProgression.purchase(
                PLAYER, PLAYER, catalogs.trees(), catalogs.skills(), snapshot, catalogs.revision(),
                TREE, CONDITIONING, new IdempotencyKey("phase10/test/purchase"),
                ProgressionCause.GAMEPLAY
        );
        var first = service.execute(plan, catalogs.revision(), new NoopProjector(), new NoopExecutor());
        var replay = service.execute(plan, catalogs.revision(), new NoopProjector(), new NoopExecutor());

        assertTrue(first.status().committed());
        assertTrue(replay.replayed());
        var after = service.snapshot(PLAYER);
        assertEquals(9, after.balances().get(POINTS));
        assertEquals(1, after.paidCosts().size());
        assertEquals(1, after.paidCosts().values().iterator().next().paidBalances().get(POINTS));
        assertEquals(1, after.ownership().size());
        assertFalse(TreeProgression.previewPurchase(
                catalogs.trees(), catalogs.skills(), after, TREE, CONDITIONING
        ).allowed());
    }

    @Test
    void refundUsesHistoricalCostAfterLiveCostEdit() throws IOException {
        Path packs = temporaryDirectory.resolve("packs");
        Catalogs original = catalogs(packs, 7);
        var service = service(original, 10, 3);
        var purchase = TreeProgression.purchase(
                PLAYER, PLAYER, original.trees(), original.skills(), service.snapshot(PLAYER),
                original.revision(), TREE, CONDITIONING,
                new IdempotencyKey("phase10/test/historical/purchase"), ProgressionCause.GAMEPLAY
        );
        assertTrue(service.execute(
                purchase, original.revision(), new NoopProjector(), new NoopExecutor()
        ).status().committed());

        Path treeFile = packs.resolve("progressiveskills-core/trees/physique_training.toml");
        Files.writeString(treeFile, Files.readString(treeFile).replaceFirst("cost = 1", "cost = 9"));
        Catalogs edited = catalogsFromExisting(packs, 8);
        var preview = TreeProgression.previewRefund(
                edited.trees(), edited.skills(), service.snapshot(PLAYER), edited.revision(),
                TREE, CONDITIONING
        );
        assertTrue(preview.allowed());
        assertEquals(1, preview.refundBalances().get(POINTS));
        assertNotEquals("0".repeat(64), preview.digest());

        var refund = TreeProgression.refund(
                PLAYER, PLAYER, edited.trees(), edited.skills(), service.snapshot(PLAYER),
                edited.revision(), TREE, CONDITIONING, preview.digest(),
                new IdempotencyKey("phase10/test/historical/refund"), ProgressionCause.GAMEPLAY
        );
        assertTrue(service.execute(
                refund, edited.revision(), new NoopProjector(), new NoopExecutor()
        ).status().committed());
        assertEquals(10, service.snapshot(PLAYER).balances().get(POINTS));
        assertTrue(service.snapshot(PLAYER).paidCosts().isEmpty());
        assertTrue(service.snapshot(PLAYER).ownership().isEmpty());
    }

    @Test
    void alternativePrerequisiteSurvivesUntilItsLastOwnedPathIsRefunded() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("packs"), 7);
        var service = service(catalogs, 20, 3);
        buy(service, catalogs, CONDITIONING, "conditioning");
        buy(service, catalogs, TECHNIQUE, "technique");
        buy(service, catalogs, RESILIENCE, "resilience");
        buy(service, catalogs, MOMENTUM, "momentum");

        var technique = TreeProgression.previewRefund(
                catalogs.trees(), catalogs.skills(), service.snapshot(PLAYER), catalogs.revision(),
                TREE, TECHNIQUE
        );
        assertEquals(List.of(TECHNIQUE), technique.affectedNodes());
        executeRefund(service, catalogs, TECHNIQUE, technique, "technique");
        assertTrue(service.snapshot(PLAYER).paidCosts().containsKey(
                TreeProgression.instance(TREE, MOMENTUM)
        ));

        var resilience = TreeProgression.previewRefund(
                catalogs.trees(), catalogs.skills(), service.snapshot(PLAYER), catalogs.revision(),
                TREE, RESILIENCE
        );
        assertEquals(List.of(MOMENTUM, RESILIENCE), resilience.affectedNodes());
        executeRefund(service, catalogs, RESILIENCE, resilience, "resilience");
        assertTrue(service.snapshot(PLAYER).paidCosts().containsKey(
                TreeProgression.instance(TREE, CONDITIONING)
        ));
        assertEquals(19, service.snapshot(PLAYER).balances().get(POINTS));
    }

    @Test
    void exactInitialSpendPersistsZeroAndCannotRematerializeInitialCurrency() throws IOException {
        Path packs = temporaryDirectory.resolve("initial-spend-packs");
        StarterPackInstaller.install(packs);
        Path currencyFile = packs.resolve("progressiveskills-core/currencies/global_points.toml");
        Files.writeString(currencyFile, Files.readString(currencyFile).replace("initial = 0", "initial = 10"));
        Path treeFile = packs.resolve("progressiveskills-core/trees/physique_training.toml");
        Files.writeString(treeFile, Files.readString(treeFile).replaceFirst("cost = 1", "cost = 10"));
        Catalogs catalogs = catalogsFromExisting(packs, 7);
        var service = uninitializedCurrencyService(3);

        buy(service, catalogs, CONDITIONING, "exact-initial");

        var after = service.snapshot(PLAYER);
        assertTrue(after.balances().containsKey(POINTS));
        assertEquals(0L, after.balances().get(POINTS));
        assertFalse(TreeProgression.previewPurchase(
                catalogs.trees(), catalogs.skills(), after, TREE, TECHNIQUE
        ).allowed());

        ProgressiveSkillsData attachment = ProgressiveSkillsData.empty(PLAYER);
        attachment.replaceTransactionState(service.exportAccount(PLAYER), catalogs.revision());
        ProgressiveSkillsData decoded = ProgressiveSkillsDataSerializer.decode(
                PLAYER, ProgressiveSkillsDataSerializer.encode(attachment)
        );

        assertTrue(decoded.active());
        assertTrue(decoded.transactionState().balances().containsKey(POINTS));
        assertEquals(0L, decoded.transactionState().balances().get(POINTS));
    }

    @Test
    void purchasePreviewHonorsNonzeroCurrencyMinimum() throws IOException {
        Path packs = temporaryDirectory.resolve("minimum-packs");
        StarterPackInstaller.install(packs);
        Path currencyFile = packs.resolve("progressiveskills-core/currencies/global_points.toml");
        String currency = Files.readString(currencyFile)
                .replace("minimum = 0", "minimum = 5")
                .replace("initial = 0", "initial = 10");
        Files.writeString(currencyFile, currency);
        Path treeFile = packs.resolve("progressiveskills-core/trees/physique_training.toml");
        Files.writeString(treeFile, Files.readString(treeFile).replaceFirst("cost = 1", "cost = 6"));
        Catalogs catalogs = catalogsFromExisting(packs, 7);

        var preview = TreeProgression.previewPurchase(
                catalogs.trees(), catalogs.skills(), uninitializedCurrencyService(3).snapshot(PLAYER),
                TREE, CONDITIONING
        );

        assertFalse(preview.allowed());
        assertTrue(preview.blockers().stream().anyMatch(message -> message.contains("below 5")));
    }

    private void buy(
            ProgressionTransactionService service,
            Catalogs catalogs,
            ResourceLocation node,
            String key
    ) {
        var plan = TreeProgression.purchase(
                PLAYER, PLAYER, catalogs.trees(), catalogs.skills(), service.snapshot(PLAYER),
                catalogs.revision(), TREE, node, new IdempotencyKey("phase10/test/buy/" + key),
                ProgressionCause.GAMEPLAY
        );
        assertTrue(service.execute(
                plan, catalogs.revision(), new NoopProjector(), new NoopExecutor()
        ).status().committed());
    }

    private void executeRefund(
            ProgressionTransactionService service,
            Catalogs catalogs,
            ResourceLocation node,
            TreeProgression.RefundPreview preview,
            String key
    ) {
        var plan = TreeProgression.refund(
                PLAYER, PLAYER, catalogs.trees(), catalogs.skills(), service.snapshot(PLAYER),
                catalogs.revision(), TREE, node, preview.digest(),
                new IdempotencyKey("phase10/test/refund/" + key), ProgressionCause.GAMEPLAY
        );
        assertTrue(service.execute(
                plan, catalogs.revision(), new NoopProjector(), new NoopExecutor()
        ).status().committed());
    }

    private static ProgressionTransactionService service(Catalogs catalogs, long points, int level) {
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

    private static ProgressionTransactionService uninitializedCurrencyService(int level) {
        var service = new ProgressionTransactionService(8, 64, 64, 64, 64, Clock.systemUTC());
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0,
                Map.of(SkillStateIds.level(PHYSIQUE), (long) level),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        ));
        return service;
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
