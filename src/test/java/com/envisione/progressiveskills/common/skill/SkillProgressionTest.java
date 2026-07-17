package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.common.transaction.TransactionStatus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProgressionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final DefinitionRevision REVISION = new DefinitionRevision(7, "7".repeat(64));

    @Test
    void highestLevelCurrencyAndAttributeOwnershipCommitTogether() {
        Fixture fixture = fixture();
        var service = ProgressionTransactionService.boundedDefaults();
        SkillAwardPlan first = award(fixture, service, "100.5", "phase7/test/one");
        var firstResult = service.execute(first.cascade(), REVISION, projector(), executor());

        assertEquals(TransactionStatus.COMMITTED, firstResult.status());
        var firstState = service.snapshot(PLAYER);
        assertEquals(1L, firstState.balances().get(fixture.currency().id()));
        assertEquals(1L, firstState.balances().get(SkillStateIds.level(fixture.skill().id())));
        assertEquals(FixedPoint.parse("100.5"), firstState.balances().get(
                SkillStateIds.activeXp(fixture.skill().id())
        ));
        assertEquals(FixedPoint.parse("2"), firstState.projectedValues().values().stream()
                .mapToLong(Long::longValue).sum());

        SkillAwardPlan second = award(fixture, service, "124.5", "phase7/test/two");
        assertEquals(TransactionStatus.COMMITTED,
                service.execute(second.cascade(), REVISION, projector(), executor()).status());
        var secondState = service.snapshot(PLAYER);
        assertEquals(2L, secondState.balances().get(fixture.currency().id()));
        assertEquals(2L, secondState.balances().get(SkillStateIds.level(fixture.skill().id())));
        assertEquals(FixedPoint.parse("4"), secondState.projectedValues().values().stream()
                .mapToLong(Long::longValue).sum());
        assertEquals(0, SkillProgress.from(fixture.skill(), secondState).intoLevelUnits());
    }

    @Test
    void persistedStateRestoresWithoutSemanticOrFixedPointDrift() {
        Fixture fixture = fixture();
        var firstService = ProgressionTransactionService.boundedDefaults();
        for (int index = 0; index < 10; index++) {
            SkillAwardPlan plan = award(fixture, firstService, "0.1", "phase7/drift/" + index);
            assertTrue(firstService.execute(plan.cascade(), REVISION, projector(), executor()).status().committed());
        }
        var persisted = firstService.exportAccount(PLAYER);

        var restoredService = ProgressionTransactionService.boundedDefaults();
        restoredService.restoreAccount(PLAYER, persisted);

        assertEquals(firstService.snapshot(PLAYER), restoredService.snapshot(PLAYER));
        assertEquals(FixedPoint.parse("1.0"), restoredService.snapshot(PLAYER).balances().get(
                SkillStateIds.activeXp(fixture.skill().id())
        ));
        assertTrue(SkillProgression.reconcile(
                PLAYER, fixture.catalog(), restoredService.snapshot(PLAYER), REVISION
        ).isEmpty());
    }

    @Test
    void capOverflowBanksWithoutDuplicatingHighestLevelRewards() {
        Fixture fixture = fixture();
        var service = ProgressionTransactionService.boundedDefaults();
        SkillAwardPlan plan = award(fixture, service, "1000", "phase7/cap/one");
        assertTrue(service.execute(plan.cascade(), REVISION, projector(), executor()).status().committed());

        var state = service.snapshot(PLAYER);
        assertEquals(fixture.skill().curve().capUnits(), state.balances().get(
                SkillStateIds.activeXp(fixture.skill().id())
        ));
        assertEquals(FixedPoint.parse("625"), state.balances().get(
                SkillStateIds.bankedXp(fixture.skill().id())
        ));
        assertEquals(3L, state.balances().get(fixture.currency().id()));

        SkillAwardPlan capped = award(fixture, service, "25", "phase7/cap/two");
        assertTrue(service.execute(capped.cascade(), REVISION, projector(), executor()).status().committed());
        assertEquals(3L, service.snapshot(PLAYER).balances().get(fixture.currency().id()));
    }

    @Test
    void nonzeroMinimumLevelAndCurrencyInitialValueMaterializeBeforeAwards() {
        Fixture fixture = fixture(5, 10);
        var service = ProgressionTransactionService.boundedDefaults();
        var reconcile = SkillProgression.reconcile(
                PLAYER, fixture.catalog(), service.snapshot(PLAYER), REVISION
        ).orElseThrow();
        assertTrue(service.execute(reconcile, REVISION, projector(), executor()).status().committed());

        var initial = service.snapshot(PLAYER);
        assertEquals(5L, initial.balances().get(SkillStateIds.level(fixture.skill().id())));
        assertEquals(5L, initial.balances().get(SkillStateIds.highestLevel(fixture.skill().id())));
        assertEquals(10L, initial.balances().get(fixture.currency().id()));

        SkillAwardPlan award = award(fixture, service, "100", "phase7/minimum/one");
        assertTrue(service.execute(award.cascade(), REVISION, projector(), executor()).status().committed());
        assertEquals(6L, service.snapshot(PLAYER).balances().get(SkillStateIds.level(fixture.skill().id())));
        assertEquals(11L, service.snapshot(PLAYER).balances().get(fixture.currency().id()));
    }

    private static SkillAwardPlan award(
            Fixture fixture,
            ProgressionTransactionService service,
            String amount,
            String key
    ) {
        return SkillProgression.award(
                PLAYER,
                PLAYER,
                fixture.skill(),
                FixedPoint.parse(amount),
                fixture.catalog(),
                service.snapshot(PLAYER),
                REVISION,
                new IdempotencyKey(key),
                SkillProgression.manualOrigin(),
                ProgressionCause.ADMIN,
                "Test skill award"
        );
    }

    private static Fixture fixture() {
        return fixture(0, 0);
    }

    private static Fixture fixture(int minLevel, long initialCurrency) {
        ResourceLocation skillId = id("progressiveskills:physique");
        ResourceLocation currencyId = id("progressiveskills:global_points");
        DefinitionPresentation presentation = presentation();
        CurrencyDefinition currency = new CurrencyDefinition(
                currencyId, presentation, 0, 1000, initialCurrency, "character"
        );
        SkillDefinition skill = new SkillDefinition(
                skillId,
                presentation,
                true,
                SkillCurve.linear(
                        minLevel, minLevel + 3, CurveRounding.CEIL,
                        new BigDecimal("100"), new BigDecimal("25")
                ),
                List.of(new SkillDefinition.CurrencyAward(
                        id("progressiveskills:physique/points"), currencyId, 1
                )),
                List.of(),
                List.of(
                        new SkillDefinition.AttributeGrant(
                                id("progressiveskills:physique/health_one"),
                                id("minecraft:generic.max_health"),
                                AttributeOperation.ADD_VALUE,
                                FixedPoint.parse("2"),
                                minLevel + 1,
                                minLevel + 1,
                                false
                        ),
                        new SkillDefinition.AttributeGrant(
                                id("progressiveskills:physique/health_scaling"),
                                id("minecraft:generic.max_health"),
                                AttributeOperation.ADD_VALUE,
                                FixedPoint.parse("2"),
                                minLevel + 2,
                                minLevel + 3,
                                true
                        )
                )
        );
        var canonicalCurrency = SkillCanonicalCodec.encode(
                new DefinitionKey(DefinitionKinds.CURRENCY, currencyId),
                currency,
                new Provenance(id("progressiveskills:test_pack"), "currencies/global_points.toml", "toml"),
                SourceMap.empty()
        );
        var canonicalSkill = SkillCanonicalCodec.encode(
                new DefinitionKey(DefinitionKinds.SKILL, skillId),
                skill,
                new Provenance(id("progressiveskills:test_pack"), "skills/physique.toml", "toml"),
                SourceMap.empty()
        );
        SkillCatalog catalog = SkillCatalog.from(CanonicalIr.of(
                List.of(canonicalCurrency, canonicalSkill), AliasMap.empty()
        ));
        return new Fixture(skill, currency, catalog);
    }

    private static DefinitionPresentation presentation() {
        return new DefinitionPresentation(
                ComponentSpec.literal("Physique"),
                Optional.empty(),
                IconSpec.single(
                        IconKind.ITEM,
                        id("minecraft:iron_chestplate"),
                        id("minecraft:barrier"),
                        ComponentSpec.literal("Iron chestplate")
                ),
                Set.of()
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
            public Optional<String> validate(
                    UUID targetId,
                    com.envisione.progressiveskills.common.transaction.TransitionAction action
            ) {
                return Optional.empty();
            }

            @Override
            public ActionExecution execute(
                    UUID targetId,
                    com.envisione.progressiveskills.common.transaction.TransactionId transactionId,
                    com.envisione.progressiveskills.common.transaction.TransitionAction action
            ) {
                return ActionExecution.success("unused");
            }
        };
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record Fixture(SkillDefinition skill, CurrencyDefinition currency, SkillCatalog catalog) {
    }
}
