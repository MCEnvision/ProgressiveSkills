package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierBehaviorSnapshot;
import com.envisione.progressiveskills.common.carrier.CarrierBindPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierCurrencyAction;
import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.carrier.CarrierMigrationPolicy;
import com.envisione.progressiveskills.common.carrier.CarrierSkillLevelAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierStackState;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.CurveRounding;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCanonicalCodec;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillCurve;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierActionPlanCompilerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001311");
    private static final ResourceLocation SKILL = id("test:physique");
    private static final ResourceLocation CURRENCY = id("test:points");
    private static final DefinitionRevision REVISION = new DefinitionRevision(13, "d".repeat(64));

    @Test
    void authoredActionsCompileAgainstVirtualStateAndCommitAtomicallyOnce() {
        Catalogs catalogs = catalogs();
        var service = ProgressionTransactionService.boundedDefaults();
        CarrierBehaviorSnapshot behavior = behavior(List.of(
                new CarrierSkillXpAction(id("test:first_xp"), SKILL, FixedPoint.parse("50"), 1),
                new CarrierSkillLevelAction(id("test:level"), SKILL, 1, 1),
                new CarrierCurrencyAction(id("test:currency"), CURRENCY, 10, 1)
        ));
        CarrierStackState state = CarrierStackState.fresh(
                1, 3, "a".repeat(64),
                UUID.fromString("00000000-0000-0000-0000-000000001312")
        );

        CarrierActionPlanCompiler.CompiledUse compiled = CarrierActionPlanCompiler.compile(
                PLAYER, behavior, state, catalogs.skills(), catalogs.trees(), service, REVISION
        );

        assertEquals(3, compiled.cascade().steps().size());
        assertEquals(1, compiled.after().stateRevision());
        assertEquals(FixedPoint.parse("100"), compiled.after().balances().get(SkillStateIds.activeXp(SKILL)));
        assertEquals(1, compiled.after().balances().get(SkillStateIds.level(SKILL)));
        assertEquals(15, compiled.after().balances().get(CURRENCY));
        var committed = service.execute(compiled.cascade(), REVISION, projector(), executor());
        assertTrue(committed.status().committed());
        assertEquals(1, service.snapshot(PLAYER).stateRevision());
        assertEquals(compiled.after().balances(), service.snapshot(PLAYER).balances());

        var replay = service.execute(compiled.cascade(), REVISION, projector(), executor());
        assertTrue(replay.replayed());
        assertEquals(1, service.snapshot(PLAYER).stateRevision());
    }

    @Test
    void laterPlanningFailureLeavesTheAuthoritativeAccountUntouched() {
        Catalogs catalogs = catalogs();
        var service = ProgressionTransactionService.boundedDefaults();
        CarrierBehaviorSnapshot behavior = behavior(List.of(
                new CarrierCurrencyAction(id("test:currency"), CURRENCY, 10, 1),
                new CarrierSkillXpAction(id("test:missing"), id("test:missing_skill"), 100, 1)
        ));
        CarrierStackState state = CarrierStackState.fresh(1, 3, "a".repeat(64), UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> CarrierActionPlanCompiler.compile(
                PLAYER, behavior, state, catalogs.skills(), catalogs.trees(), service, REVISION
        ));

        assertEquals(0, service.snapshot(PLAYER).stateRevision());
        assertTrue(service.snapshot(PLAYER).balances().isEmpty());
    }

    private static Catalogs catalogs() {
        DefinitionPresentation presentation = new DefinitionPresentation(
                ComponentSpec.literal("Test"),
                Optional.empty(),
                IconSpec.single(
                        IconKind.ITEM,
                        id("minecraft:book"),
                        id("minecraft:barrier"),
                        ComponentSpec.literal("Test")
                ),
                Set.of()
        );
        CurrencyDefinition currency = new CurrencyDefinition(
                CURRENCY, presentation, 0, 1_000, 5, "character"
        );
        SkillDefinition skill = new SkillDefinition(
                SKILL,
                presentation,
                true,
                SkillCurve.flat(0, 3, CurveRounding.CEIL, new BigDecimal("100")),
                List.of(),
                List.of(),
                List.of()
        );
        Provenance provenance = new Provenance(id("test:pack"), "test.toml", "toml");
        CanonicalIr ir = CanonicalIr.of(List.of(
                SkillCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.CURRENCY, CURRENCY),
                        currency,
                        provenance,
                        SourceMap.empty()
                ),
                SkillCanonicalCodec.encode(
                        new DefinitionKey(DefinitionKinds.SKILL, SKILL),
                        skill,
                        provenance,
                        SourceMap.empty()
                )
        ), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        return new Catalogs(skills, TreeCatalog.from(ir, skills));
    }

    private static CarrierBehaviorSnapshot behavior(List<com.envisione.progressiveskills.common.carrier.CarrierUseAction> actions) {
        return new CarrierBehaviorSnapshot(
                id("test:carrier"),
                CarrierKind.TOME,
                1,
                CarrierMigrationPolicy.KEEP_PINNED,
                CarrierBindPolicy.NONE,
                CarrierDeliveryPolicy.PENDING_CLAIM,
                1,
                3,
                20,
                actions
        );
    }

    private static PersistentProjector projector() {
        return new PersistentProjector() {
            @Override
            public Optional<String> validate(UUID targetId, List<com.envisione.progressiveskills.common.transaction.ProjectionChange> changes) {
                return Optional.empty();
            }

            @Override
            public void apply(UUID targetId, List<com.envisione.progressiveskills.common.transaction.ProjectionChange> changes) {
            }
        };
    }

    private static TransitionActionExecutor executor() {
        return new TransitionActionExecutor() {
            @Override
            public Optional<String> validate(UUID targetId, TransitionAction action) {
                return Optional.empty();
            }

            @Override
            public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
                return ActionExecution.success("unused");
            }
        };
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(SkillCatalog skills, TreeCatalog trees) {
    }
}
