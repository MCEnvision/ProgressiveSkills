package com.envisione.progressiveskills.common.classdef;

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
import com.envisione.progressiveskills.common.transaction.PurchaseInstanceId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.server.pack.StarterPackInstaller;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassProgressionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final ResourceLocation POINTS = id("progressiveskills:global_points");
    private static final ResourceLocation PHYSIQUE = id("progressiveskills:physique");
    private static final ResourceLocation WARRIOR = id("progressiveskills:warrior");
    private static final ResourceLocation SCHOLAR = id("progressiveskills:scholar");
    private static final ResourceLocation GUARD = id("progressiveskills:warrior_guard");
    private static final ResourceLocation INSIGHT = id("progressiveskills:combat_insight");

    @TempDir
    Path temporaryDirectory;

    @Test
    void paidSelectionSinksExactCostPersistsMarkersAndNeverReplaysStarterKit() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("paid"), 11);
        ProgressionTransactionService service = service(10, 3, Map.of());
        RecordingExecutor executor = new RecordingExecutor();

        TransactionResult selected = execute(
                service,
                select(service, catalogs, WARRIOR, "paid/select"),
                catalogs.revision(),
                new NoopProjector(),
                executor
        );

        assertTrue(selected.status().committed());
        ProgressionSnapshot afterSelect = service.snapshot(PLAYER);
        assertEquals(9L, afterSelect.balances().get(POINTS));
        assertEquals(Set.of(WARRIOR), ClassProgression.selectedClasses(afterSelect));
        assertEquals(Set.of(WARRIOR), ClassProgression.activeClasses(afterSelect));
        assertEquals(1, afterSelect.paidCosts().size());
        assertEquals(1L, afterSelect.paidCosts().values().iterator().next().paidBalances().get(POINTS));
        assertEquals(1L, afterSelect.projectedValues().get(selectedKey(WARRIOR)));
        assertEquals(1L, afterSelect.projectedValues().get(activeKey(WARRIOR)));
        assertEquals(1, executor.executed.size());
        assertEquals(ClassStarterKitAction.TYPE, executor.executed.getFirst().type());
        assertEquals("minecraft:wooden_sword=1", executor.executed.getFirst().payload());
        assertEquals("warrior/starter_kit", executor.executed.getFirst().source().grantId().getPath());

        var restored = service(0, 0, Map.of());
        restored.restoreAccount(PLAYER, service.exportAccount(PLAYER));
        assertEquals(Set.of(WARRIOR), ClassProgression.selectedClasses(restored.snapshot(PLAYER)));
        assertEquals(Set.of(WARRIOR), ClassProgression.activeClasses(restored.snapshot(PLAYER)));

        ClassProgression.ChangePreview respecPreview = ClassProgression.previewRespec(
                catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER), catalogs.revision(), WARRIOR
        );
        assertEquals(Map.of(POINTS, 1L), respecPreview.costBalances());
        assertTrue(execute(
                service,
                ClassProgression.respec(
                        PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER),
                        catalogs.revision(), WARRIOR, respecPreview.digest(),
                        new IdempotencyKey("phase11/test/paid/respec"), ProgressionCause.GAMEPLAY
                ),
                catalogs.revision(),
                new NoopProjector(),
                executor
        ).status().committed());
        assertEquals(8L, service.snapshot(PLAYER).balances().get(POINTS));
        assertTrue(service.snapshot(PLAYER).paidCosts().isEmpty());

        assertTrue(execute(
                service,
                select(service, catalogs, WARRIOR, "paid/reselect"),
                catalogs.revision(),
                new NoopProjector(),
                executor
        ).status().committed());
        assertEquals(7L, service.snapshot(PLAYER).balances().get(POINTS));
        assertEquals(1, executor.executed.size());
    }

    @Test
    void freeSelectionUsesDurableSelectedAndActiveMarkersWithoutPaidEvidence() throws IOException {
        Path packs = temporaryDirectory.resolve("free");
        StarterPackInstaller.install(packs);
        Path scholarFile = classFile(packs, "scholar");
        Files.writeString(scholarFile, Files.readString(scholarFile).replace(
                "selection_cost = { currency = \"progressiveskills:global_points\", amount = 1 }\n", ""
        ));
        Catalogs catalogs = catalogsFromExisting(packs, 12);
        ProgressionTransactionService service = service(10, 3, Map.of());

        CascadePlan plan = select(service, catalogs, SCHOLAR, "free/select");

        assertTrue(plan.transaction().rootStep().balanceMutations().isEmpty());
        assertTrue(plan.transaction().rootStep().paidCostMutations().isEmpty());
        assertTrue(execute(
                service, plan, catalogs.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        ProgressionSnapshot after = service.snapshot(PLAYER);
        assertEquals(10L, after.balances().get(POINTS));
        assertTrue(after.paidCosts().isEmpty());
        assertEquals(Set.of(SCHOLAR), ClassProgression.selectedClasses(after));
        assertEquals(Set.of(SCHOLAR), ClassProgression.activeClasses(after));

        ProgressionTransactionService restored = service(0, 0, Map.of());
        restored.restoreAccount(PLAYER, service.exportAccount(PLAYER));
        assertEquals(Set.of(SCHOLAR), ClassProgression.selectedClasses(restored.snapshot(PLAYER)));
        assertEquals(Set.of(SCHOLAR), ClassProgression.activeClasses(restored.snapshot(PLAYER)));
    }

    @Test
    void starterKitActionsRemainStableAcrossItemReorderAndAggregateDuplicates() throws IOException {
        Path packs = temporaryDirectory.resolve("kit-order");
        StarterPackInstaller.install(packs);
        Path warriorFile = classFile(packs, "warrior");
        String original = Files.readString(warriorFile);
        Files.writeString(warriorFile, original.replace(
                "starter_kit = [\"minecraft:wooden_sword\"]",
                "starter_kit = [\"minecraft:stick\", \"minecraft:wooden_sword\", \"minecraft:stick\"]"
        ));
        Catalogs first = catalogsFromExisting(packs, 13);
        List<TransitionAction> firstActions = select(
                service(10, 3, Map.of()), first, WARRIOR, "kit/first"
        ).transaction().rootStep().transitionActions();

        Files.writeString(warriorFile, original.replace(
                "starter_kit = [\"minecraft:wooden_sword\"]",
                "starter_kit = [\"minecraft:wooden_sword\", \"minecraft:stick\", \"minecraft:stick\"]"
        ));
        Catalogs reordered = catalogsFromExisting(packs, 14);
        List<TransitionAction> reorderedActions = select(
                service(10, 3, Map.of()), reordered, WARRIOR, "kit/second"
        ).transaction().rootStep().transitionActions();

        assertEquals(firstActions, reorderedActions);
        assertEquals(1, firstActions.size());
        TransitionAction action = firstActions.getFirst();
        assertEquals(ClassStarterKitAction.TYPE, action.type());
        assertEquals(3L, action.amount());
        assertEquals("minecraft:stick=2,minecraft:wooden_sword=1", action.payload());
        assertEquals("warrior/starter_kit", action.source().grantId().getPath());
        assertEquals(Map.of(id("minecraft:stick"), 2, id("minecraft:wooden_sword"), 1),
                ClassStarterKitAction.decode(action.payload()));
    }

    @Test
    void classAndExternalSourcesCoownOneTargetAndSynergyRevokesCleanly() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("coownership"), 15);
        EntitlementKey guardKey = new EntitlementKey(
                ClassGrantType.ABILITY.entitlementType().orElseThrow(), GUARD
        );
        GrantSourceId external = new GrantSourceId(
                id("progressiveskills:admin"), id("progressiveskills:test"), id("progressiveskills:test/guard")
        );
        ProgressionTransactionService service = service(10, 3, Map.of(
                guardKey, Map.of(external, new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION))
        ));

        assertTrue(execute(service, select(service, catalogs, WARRIOR, "coown/warrior"),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());
        assertTrue(execute(service, select(service, catalogs, SCHOLAR, "coown/scholar"),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());

        ProgressionSnapshot combined = service.snapshot(PLAYER);
        assertEquals(2, combined.ownership().get(guardKey).size());
        EntitlementKey insightKey = new EntitlementKey(
                ClassGrantType.ABILITY.entitlementType().orElseThrow(), INSIGHT
        );
        assertNotNull(combined.ownership().get(insightKey));
        assertEquals(1L, combined.projectedValues().get(insightKey));

        ClassProgression.ChangePreview preview = ClassProgression.previewRespec(
                catalogs.classes(), catalogs.skills(), combined, catalogs.revision(), WARRIOR
        );
        assertTrue(execute(
                service,
                ClassProgression.respec(
                        PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER),
                        catalogs.revision(), WARRIOR, preview.digest(),
                        new IdempotencyKey("phase11/test/coown/respec"), ProgressionCause.GAMEPLAY
                ),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());

        ProgressionSnapshot after = service.snapshot(PLAYER);
        assertEquals(Map.of(external, new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION)),
                after.ownership().get(guardKey));
        assertEquals(1L, after.projectedValues().get(guardKey));
        assertFalse(after.ownership().containsKey(insightKey));
        assertEquals(Set.of(SCHOLAR), ClassProgression.activeClasses(after));
    }

    @Test
    void capacityAccessAndSkillAndClassPrerequisitesFailClosed() throws IOException {
        Catalogs base = catalogs(temporaryDirectory.resolve("requirements-level"), 16);
        assertFalse(ClassProgression.previewSelect(
                base.classes(), base.skills(), service(10, 0, Map.of()).snapshot(PLAYER), WARRIOR
        ).allowed());

        Path capacityPacks = temporaryDirectory.resolve("requirements-capacity");
        StarterPackInstaller.install(capacityPacks);
        Path slot = capacityPacks.resolve("progressiveskills-core/class_slots/combat.toml");
        Files.writeString(slot, Files.readString(slot).replace("capacity = 2", "capacity = 1"));
        Catalogs capacity = catalogsFromExisting(capacityPacks, 17);
        ProgressionTransactionService capacityService = service(10, 3, Map.of());
        assertTrue(execute(
                capacityService, select(capacityService, capacity, WARRIOR, "capacity/warrior"),
                capacity.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        ClassProgression.ChangePreview capacityBlocked = ClassProgression.previewSelect(
                capacity.classes(), capacity.skills(), capacityService.snapshot(PLAYER), SCHOLAR
        );
        assertFalse(capacityBlocked.allowed());
        assertTrue(capacityBlocked.blockers().stream().anyMatch(value -> value.contains("capacity")));

        Path prerequisitePacks = temporaryDirectory.resolve("requirements-class");
        StarterPackInstaller.install(prerequisitePacks);
        Path scholar = classFile(prerequisitePacks, "scholar");
        Files.writeString(scholar, Files.readString(scholar).replace(
                "nodes = [], classes = []",
                "nodes = [], classes = [\"progressiveskills:warrior\"]"
        ));
        Catalogs prerequisites = catalogsFromExisting(prerequisitePacks, 18);
        ProgressionTransactionService prerequisiteService = service(10, 3, Map.of());
        assertFalse(ClassProgression.previewSelect(
                prerequisites.classes(), prerequisites.skills(), prerequisiteService.snapshot(PLAYER), SCHOLAR
        ).allowed());
        assertTrue(execute(
                prerequisiteService,
                select(prerequisiteService, prerequisites, WARRIOR, "prerequisite/warrior"),
                prerequisites.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        assertTrue(ClassProgression.previewSelect(
                prerequisites.classes(), prerequisites.skills(), prerequisiteService.snapshot(PLAYER), SCHOLAR
        ).allowed());

        Path accessPacks = temporaryDirectory.resolve("requirements-access");
        StarterPackInstaller.install(accessPacks);
        Path accessScholar = classFile(accessPacks, "scholar");
        Files.writeString(accessScholar, Files.readString(accessScholar).replace(
                "access_required = false", "access_required = true"
        ));
        Catalogs access = catalogsFromExisting(accessPacks, 19);
        ProgressionTransactionService locked = service(10, 3, Map.of());
        assertFalse(ClassProgression.previewSelect(
                access.classes(), access.skills(), locked.snapshot(PLAYER), SCHOLAR
        ).allowed());
        EntitlementKey accessKey = new EntitlementKey(
                ClassGrantType.CLASS_ACCESS.entitlementType().orElseThrow(), SCHOLAR
        );
        ProgressionTransactionService unlocked = service(10, 3, Map.of(
                accessKey, Map.of(
                        new GrantSourceId(id("progressiveskills:admin"), SCHOLAR, id("progressiveskills:test/access")),
                        new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION)
                )
        ));
        assertTrue(ClassProgression.previewSelect(
                access.classes(), access.skills(), unlocked.snapshot(PLAYER), SCHOLAR
        ).allowed());
    }

    @Test
    void classAccessIsAnInitialSelectionGateAndLossDoesNotSuspendASelectedClass() throws IOException {
        Path packs = temporaryDirectory.resolve("access-retention");
        StarterPackInstaller.install(packs);
        Path scholar = classFile(packs, "scholar");
        Files.writeString(scholar, Files.readString(scholar).replace(
                "access_required = false", "access_required = true"
        ));
        Path warrior = classFile(packs, "warrior");
        Files.writeString(warrior, Files.readString(warrior) + """

                [[grants]]
                id = "progressiveskills:warrior/scholar_access"
                type = "class_access"
                class = "progressiveskills:scholar"
                """);
        Catalogs catalogs = catalogsFromExisting(packs, 20);
        ProgressionTransactionService service = service(20, 3, Map.of());

        assertTrue(execute(service, select(service, catalogs, WARRIOR, "access/warrior"),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());
        assertTrue(ClassProgression.previewSelect(
                catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER), SCHOLAR
        ).allowed());
        assertTrue(execute(service, select(service, catalogs, SCHOLAR, "access/scholar"),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());

        ClassProgression.ChangePreview removeProvider = ClassProgression.previewRespec(
                catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER), catalogs.revision(), WARRIOR
        );
        assertTrue(execute(
                service,
                ClassProgression.respec(
                        PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER),
                        catalogs.revision(), WARRIOR, removeProvider.digest(),
                        new IdempotencyKey("phase11/test/access/remove"), ProgressionCause.GAMEPLAY
                ),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        assertEquals(Set.of(SCHOLAR), ClassProgression.selectedClasses(service.snapshot(PLAYER)));
        assertEquals(Set.of(SCHOLAR), ClassProgression.activeClasses(service.snapshot(PLAYER)));

        assertTrue(execute(service, select(service, catalogs, WARRIOR, "access/restore"),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());
        assertEquals(Set.of(WARRIOR, SCHOLAR), ClassProgression.activeClasses(service.snapshot(PLAYER)));
    }

    @Test
    void swapIsAtomicAndRejectsAStalePreview() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("swap"), 21);
        ProgressionTransactionService service = service(20, 3, Map.of());
        assertTrue(execute(service, select(service, catalogs, WARRIOR, "swap/warrior"),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());
        ProgressionSnapshot before = service.snapshot(PLAYER);
        ClassProgression.ChangePreview preview = ClassProgression.previewSwap(
                catalogs.classes(), catalogs.skills(), before, catalogs.revision(), WARRIOR, SCHOLAR
        );
        assertTrue(preview.allowed());
        assertEquals(Map.of(POINTS, 2L), preview.costBalances());
        ProgressionSnapshot advanced = new ProgressionSnapshot(
                before.stateRevision() + 1,
                before.balances(), before.ownership(), before.paidCosts(), before.projectedValues(),
                before.receiptCount(), before.idempotencyCount(), before.auditCount()
        );
        var stale = assertThrows(IllegalArgumentException.class, () -> ClassProgression.swap(
                PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), advanced, catalogs.revision(),
                WARRIOR, SCHOLAR, preview.digest(), new IdempotencyKey("phase11/test/swap/stale"),
                ProgressionCause.GAMEPLAY
        ));
        assertEquals("Class change preview is stale", stale.getMessage());

        CascadePlan rejectedPlan = ClassProgression.swap(
                PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER),
                catalogs.revision(), WARRIOR, SCHOLAR, preview.digest(),
                new IdempotencyKey("phase11/test/swap/rejected"), ProgressionCause.GAMEPLAY
        );
        TransactionResult rejected = execute(
                service, rejectedPlan, catalogs.revision(), new RejectingProjector(), new RecordingExecutor()
        );
        assertFalse(rejected.status().committed());
        assertEquals(before.balances(), service.snapshot(PLAYER).balances());
        assertEquals(before.ownership(), service.snapshot(PLAYER).ownership());
        assertEquals(before.paidCosts(), service.snapshot(PLAYER).paidCosts());

        CascadePlan committedPlan = ClassProgression.swap(
                PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER),
                catalogs.revision(), WARRIOR, SCHOLAR, preview.digest(),
                new IdempotencyKey("phase11/test/swap/committed"), ProgressionCause.GAMEPLAY
        );
        assertTrue(execute(
                service, committedPlan, catalogs.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        ProgressionSnapshot after = service.snapshot(PLAYER);
        assertEquals(17L, after.balances().get(POINTS));
        assertEquals(Set.of(SCHOLAR), ClassProgression.selectedClasses(after));
        assertEquals(Set.of(SCHOLAR), ClassProgression.activeClasses(after));
        assertFalse(after.paidCosts().containsKey(classInstance(WARRIOR)));
        assertTrue(after.paidCosts().containsKey(classInstance(SCHOLAR)));
    }

    @Test
    void reconciliationSuspendsEntireOverCapacitySlotAndRestoresWithoutActionsOrRefund() throws IOException {
        Path packs = temporaryDirectory.resolve("reconcile-capacity");
        Catalogs original = catalogs(packs, 22);
        ProgressionTransactionService service = service(20, 3, Map.of());
        RecordingExecutor executor = new RecordingExecutor();
        assertTrue(execute(service, select(service, original, WARRIOR, "reconcile/warrior"),
                original.revision(), new NoopProjector(), executor).status().committed());
        assertTrue(execute(service, select(service, original, SCHOLAR, "reconcile/scholar"),
                original.revision(), new NoopProjector(), executor).status().committed());
        int acquisitionActions = executor.executed.size();
        assertEquals(Set.of(WARRIOR, SCHOLAR), ClassProgression.activeClasses(service.snapshot(PLAYER)));
        assertEquals(18L, service.snapshot(PLAYER).balances().get(POINTS));

        Path slot = packs.resolve("progressiveskills-core/class_slots/combat.toml");
        String slotSource = Files.readString(slot);
        Files.writeString(slot, slotSource.replace("capacity = 2", "capacity = 1"));
        Catalogs constrained = catalogsFromExisting(packs, 23);
        CascadePlan suspend = ClassProgression.reconcile(
                PLAYER, constrained.classes(), constrained.skills(), service.snapshot(PLAYER),
                constrained.revision()
        ).orElseThrow();
        assertTrue(suspend.steps().stream().allMatch(step -> step.transitionActions().isEmpty()));
        assertTrue(execute(
                service, suspend, constrained.revision(), new NoopProjector(), executor
        ).status().committed());
        ProgressionSnapshot suspended = service.snapshot(PLAYER);
        assertEquals(Set.of(WARRIOR, SCHOLAR), ClassProgression.selectedClasses(suspended));
        assertTrue(ClassProgression.activeClasses(suspended).isEmpty());
        assertEquals(2, suspended.paidCosts().size());
        assertEquals(18L, suspended.balances().get(POINTS));
        assertEquals(acquisitionActions, executor.executed.size());

        Files.writeString(slot, slotSource);
        Catalogs restored = catalogsFromExisting(packs, 24);
        CascadePlan reactivate = ClassProgression.reconcile(
                PLAYER, restored.classes(), restored.skills(), service.snapshot(PLAYER), restored.revision()
        ).orElseThrow();
        assertTrue(execute(
                service, reactivate, restored.revision(), new NoopProjector(), executor
        ).status().committed());
        assertEquals(Set.of(WARRIOR, SCHOLAR), ClassProgression.activeClasses(service.snapshot(PLAYER)));
        assertEquals(18L, service.snapshot(PLAYER).balances().get(POINTS));
        assertEquals(acquisitionActions, executor.executed.size());
        assertTrue(ClassProgression.reconcile(
                PLAYER, restored.classes(), restored.skills(), service.snapshot(PLAYER), restored.revision()
        ).isEmpty());
    }

    @Test
    void compatibleNumericReloadUpdatesGrantWhileStructuralLineageChangeSuspendsSelection() throws IOException {
        Path packs = temporaryDirectory.resolve("reconcile-lineage");
        Catalogs original = catalogs(packs, 25);
        ProgressionTransactionService service = service(10, 3, Map.of());
        assertTrue(execute(service, select(service, original, WARRIOR, "lineage/select"),
                original.revision(), new NoopProjector(), new RecordingExecutor()).status().committed());
        String originalLineage = service.snapshot(PLAYER).paidCosts().get(classInstance(WARRIOR)).ownerLineage();
        Path warrior = classFile(packs, "warrior");
        String source = Files.readString(warrior);

        Files.writeString(warrior, source.replace("value = 1.0", "value = 2.0"));
        Catalogs numeric = catalogsFromExisting(packs, 26);
        assertEquals(originalLineage, numeric.classes().classLineageFingerprint(WARRIOR));
        assertTrue(execute(
                service,
                ClassProgression.reconcile(
                        PLAYER, numeric.classes(), numeric.skills(), service.snapshot(PLAYER), numeric.revision()
                ).orElseThrow(),
                numeric.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        EntitlementKey damageKey = new EntitlementKey(
                com.envisione.progressiveskills.common.skill.AttributeOperation.ADD_VALUE.targetType(),
                id("minecraft:generic.attack_damage")
        );
        assertEquals(FixedPoint.parse("2"), service.snapshot(PLAYER).projectedValues().get(damageKey));
        assertEquals(Set.of(WARRIOR), ClassProgression.activeClasses(service.snapshot(PLAYER)));

        Files.writeString(warrior, source.replace(
                "attribute = \"minecraft:generic.attack_damage\"",
                "attribute = \"minecraft:generic.armor\""
        ));
        Catalogs structural = catalogsFromExisting(packs, 27);
        assertFalse(originalLineage.equals(structural.classes().classLineageFingerprint(WARRIOR)));
        assertTrue(execute(
                service,
                ClassProgression.reconcile(
                        PLAYER, structural.classes(), structural.skills(), service.snapshot(PLAYER),
                        structural.revision()
                ).orElseThrow(),
                structural.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        assertEquals(Set.of(WARRIOR), ClassProgression.selectedClasses(service.snapshot(PLAYER)));
        assertTrue(ClassProgression.activeClasses(service.snapshot(PLAYER)).isEmpty());
        assertTrue(service.snapshot(PLAYER).paidCosts().containsKey(classInstance(WARRIOR)));
        assertEquals(9L, service.snapshot(PLAYER).balances().get(POINTS));
    }

    @Test
    void forgedLogicalMarkerSourcesAreRevokedInsteadOfBecomingSelections() throws IOException {
        Catalogs catalogs = catalogs(temporaryDirectory.resolve("forged-markers"), 28);
        EntitlementKey selected = selectedKey(WARRIOR);
        EntitlementKey active = activeKey(WARRIOR);
        ProgressionTransactionService service = service(10, 3, Map.of(
                selected, Map.of(
                        new GrantSourceId(ClassProgression.SELECTION_OWNER_KIND, WARRIOR, id("progressiveskills:wrong")),
                        new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION)
                ),
                active, Map.of(
                        new GrantSourceId(ClassProgression.ACTIVE_OWNER_KIND, WARRIOR, id("progressiveskills:wrong_active")),
                        new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION)
                )
        ));

        assertTrue(ClassProgression.selectedClasses(service.snapshot(PLAYER)).isEmpty());
        assertTrue(ClassProgression.activeClasses(service.snapshot(PLAYER)).isEmpty());
        assertTrue(execute(
                service,
                ClassProgression.reconcile(
                        PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER), catalogs.revision()
                ).orElseThrow(),
                catalogs.revision(), new NoopProjector(), new RecordingExecutor()
        ).status().committed());
        assertFalse(service.snapshot(PLAYER).ownership().containsKey(selected));
        assertFalse(service.snapshot(PLAYER).ownership().containsKey(active));
    }

    private static CascadePlan select(
            ProgressionTransactionService service,
            Catalogs catalogs,
            ResourceLocation classId,
            String key
    ) {
        return ClassProgression.select(
                PLAYER, PLAYER, catalogs.classes(), catalogs.skills(), service.snapshot(PLAYER),
                catalogs.revision(), classId, new IdempotencyKey("phase11/test/" + key),
                ProgressionCause.GAMEPLAY
        );
    }

    private static TransactionResult execute(
            ProgressionTransactionService service,
            CascadePlan plan,
            DefinitionRevision revision,
            PersistentProjector projector,
            TransitionActionExecutor executor
    ) {
        return service.execute(plan, revision, projector, executor);
    }

    private static ProgressionTransactionService service(
            long points,
            int level,
            Map<EntitlementKey, Map<GrantSourceId, EntitlementContribution>> ownership
    ) {
        var service = new ProgressionTransactionService(8, 128, 128, 128, 128, Clock.systemUTC());
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0,
                Map.of(POINTS, points, SkillStateIds.level(PHYSIQUE), (long) level),
                ownership,
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
        TreeCatalog trees = TreeCatalog.from(snapshot.canonicalIr(), skills);
        return new Catalogs(
                skills,
                trees,
                ClassCatalog.from(snapshot.canonicalIr(), skills, trees),
                new DefinitionRevision(generation, snapshot.contentDigest())
        );
    }

    private static Path classFile(Path packs, String name) {
        return packs.resolve("progressiveskills-core/classes/" + name + ".toml");
    }

    private static EntitlementKey selectedKey(ResourceLocation classId) {
        return new EntitlementKey(ClassEntitlementTypes.SELECTED, classId);
    }

    private static EntitlementKey activeKey(ResourceLocation classId) {
        return new EntitlementKey(ClassEntitlementTypes.ACTIVE, classId);
    }

    private static PurchaseInstanceId classInstance(ResourceLocation classId) {
        return new PurchaseInstanceId(DefinitionKinds.CLASS.id(), classId, classId, 1);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(
            SkillCatalog skills,
            TreeCatalog trees,
            ClassCatalog classes,
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

    private static final class RejectingProjector implements PersistentProjector {
        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return Optional.of("Rejected for atomicity test");
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
            throw new AssertionError("Rejected projection must not apply");
        }
    }

    private static final class RecordingExecutor implements TransitionActionExecutor {
        private final List<TransitionAction> executed = new ArrayList<>();

        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            executed.add(action);
            return ActionExecution.success("recorded");
        }
    }
}
