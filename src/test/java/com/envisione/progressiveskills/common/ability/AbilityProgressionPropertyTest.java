package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.EntitlementContribution;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistedTransactionState;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.minecraft.resources.ResourceLocation;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityProgressionPropertyTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final ResourceLocation ABILITY = id("progressiveskills:charged_burst");
    private static final ResourceLocation GROUP = id("progressiveskills:charged_group");
    private static final DefinitionRevision REVISION = new DefinitionRevision(12, "c".repeat(64));

    @Property(tries = 300)
    void onlineRechargeNeverExceedsBoundsAndResumesExactly(
            @ForAll @IntRange(min = 1, max = 1_000) int rechargeTicks,
            @ForAll @IntRange(min = 0, max = 5_000) int elapsedTicks
    ) {
        Catalogs catalogs = catalogs(rechargeTicks);
        AbilityCatalog catalog = catalogs.abilities();
        SkillCatalog skills = catalogs.skills();
        ProgressionTransactionService service = service();
        long activationTick = 100;

        for (int index = 0; index < 3; index++) {
            var plan = AbilityProgression.activate(
                    PLAYER, PLAYER, catalog, skills, service.snapshot(PLAYER), REVISION,
                    ABILITY, activationTick,
                    new IdempotencyKey("phase12/property/activate/" + index),
                    ProgressionCause.GAMEPLAY
            );
            assertTrue(service.execute(
                    plan.transaction(), REVISION, new NoopProjector(), new NoopExecutor()
            ).status().committed());
        }

        AbilityState empty = AbilityProgression.state(catalog, service.snapshot(PLAYER), activationTick);
        assertEquals(0, empty.charges().get(ABILITY).current());
        assertEquals(activationTick + rechargeTicks, empty.charges().get(ABILITY).nextRechargeTick());

        long currentTick = activationTick + elapsedTicks;
        var recharge = AbilityProgression.recharge(
                PLAYER, catalog, service.snapshot(PLAYER), REVISION, currentTick
        );
        recharge.ifPresent(plan -> {
            var first = service.execute(plan, REVISION, new NoopProjector(), new NoopExecutor());
            var replay = service.execute(plan, REVISION, new NoopProjector(), new NoopExecutor());
            assertTrue(first.status().committed());
            assertTrue(replay.replayed());
        });

        int expected = Math.min(3, elapsedTicks / rechargeTicks);
        long expectedNext = expected == 3 ? 0 : activationTick + (long) (expected + 1) * rechargeTicks;
        AbilityState after = AbilityProgression.state(catalog, service.snapshot(PLAYER), currentTick);
        assertEquals(expected, after.charges().get(ABILITY).current());
        assertEquals(expectedNext, after.charges().get(ABILITY).nextRechargeTick());

        ProgressionTransactionService restored = emptyService();
        restored.restoreAccount(PLAYER, service.exportAccount(PLAYER));
        assertEquals(after, AbilityProgression.state(catalog, restored.snapshot(PLAYER), currentTick));
    }

    private static Catalogs catalogs(int rechargeTicks) {
        AbilityDefinition definition = new AbilityDefinition(
                ABILITY, presentation(), true, AbilityKind.ACTIVE, true, false,
                List.of(), List.of(), AbilityTargeting.SELF, GROUP,
                0, 3, rechargeTicks,
                List.of(new AbilityHealAction(id("progressiveskills:charged_burst/heal"), FixedPoint.SCALE))
        );
        Provenance provenance = new Provenance(id("progressiveskills:property_pack"), "memory", "test");
        CanonicalIr ir = CanonicalIr.of(List.of(AbilityCanonicalCodec.encode(
                new DefinitionKey(DefinitionKinds.ABILITY, ABILITY),
                definition, provenance, SourceMap.empty()
        )), AliasMap.empty());
        SkillCatalog skills = SkillCatalog.from(ir);
        TreeCatalog trees = TreeCatalog.from(ir, skills);
        ClassCatalog classes = ClassCatalog.from(ir, skills, trees);
        return new Catalogs(skills, AbilityCatalog.from(ir, skills, classes));
    }

    private static ProgressionTransactionService service() {
        ProgressionTransactionService service = emptyService();
        ResourceLocation slot = AbilityState.slotId(0);
        service.restoreAccount(PLAYER, new PersistedTransactionState(
                0,
                Map.of(),
                Map.of(
                        new EntitlementKey(AbilityEntitlementTypes.OWNED, ABILITY), Map.of(
                                new GrantSourceId(
                                        id("progressiveskills:test"), id("progressiveskills:owner"), ABILITY
                                ), new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION)
                        ),
                        new EntitlementKey(AbilityEntitlementTypes.SLOT_ASSIGNMENT, slot), Map.of(
                                new GrantSourceId(AbilityProgression.ASSIGNMENT_OWNER_KIND, ABILITY, slot),
                                new EntitlementContribution(1, EntitlementResolver.BOOLEAN_UNION)
                        )
                ),
                Map.of(), Map.of(), Map.of(), List.of()
        ));
        return service;
    }

    private static ProgressionTransactionService emptyService() {
        return new ProgressionTransactionService(8, 64, 64, 64, 64, Clock.systemUTC());
    }

    private static DefinitionPresentation presentation() {
        ComponentSpec text = ComponentSpec.literal("Charged burst");
        return new DefinitionPresentation(
                text,
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, id("minecraft:blaze_powder"), id("minecraft:barrier"), text),
                Set.of()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Catalogs(SkillCatalog skills, AbilityCatalog abilities) {
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
            return ActionExecution.success("executed");
        }
    }
}
