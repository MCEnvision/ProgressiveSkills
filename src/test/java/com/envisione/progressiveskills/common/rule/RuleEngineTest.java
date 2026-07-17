package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final DefinitionRevision REVISION = new DefinitionRevision(8, "8".repeat(64));

    @Test
    void registriesRejectDuplicatesAndUnsupportedMatcherSubjects() {
        assertEquals(RuleSubjectType.BLOCK,
                RuleTriggerRegistry.core().require(RuleTriggerRegistry.BLOCK_BREAK).subjectType());
        assertTrue(RuleMatcherRegistry.core().require("tag").supports(RuleSubjectType.BLOCK));
        assertFalse(RuleMatcherRegistry.core().require("school").supports(RuleSubjectType.BLOCK));
        assertEquals(id("progressiveskills:neoforge"),
                RuleProviderRegistry.core().requireProvider(RuleTriggerRegistry.BLOCK_BREAK));

        var triggers = RuleTriggerRegistry.builder().register(id("test:one"), RuleSubjectType.CUSTOM);
        assertThrows(IllegalArgumentException.class,
                () -> triggers.register(id("test:one"), RuleSubjectType.CUSTOM));
        var providers = RuleProviderRegistry.builder().register(id("test:one"), Set.of(id("test:trigger")));
        assertThrows(IllegalArgumentException.class,
                () -> providers.register(id("test:two"), Set.of(id("test:trigger"))));
    }

    @Test
    void matchersNormalizePrefixesBareIdsNegationAndCustomNameOptIn() {
        RuleMatcherRegistry registry = RuleMatcherRegistry.core();
        RuleMatcherSpec tag = RuleMatcherSpec.parse("!tag:minecraft:logs", registry, false);
        assertTrue(tag.negated());
        assertEquals(id("minecraft:logs"), tag.idValue());
        assertEquals("!tag:minecraft:logs", tag.serialized());
        assertEquals("example", RuleMatcherSpec.parse("mod:example", registry, false).value());
        assertEquals("id:minecraft:stone",
                RuleMatcherSpec.parse("minecraft:stone", registry, false).serialized());
        assertEquals("!id:minecraft:dirt",
                RuleMatcherSpec.parse("!minecraft:dirt", registry, false).serialized());
        assertThrows(IllegalArgumentException.class,
                () -> RuleMatcherSpec.parse("custom_name:Player Sword", registry, false));
        assertEquals("player sword",
                RuleMatcherSpec.parse("custom_name:Player Sword", registry, true).value());
        assertThrows(IllegalArgumentException.class,
                () -> RuleMatcherSpec.parse("unknown_prefix:minecraft:stone", registry, false));
    }

    @Test
    void literalMultiplierGroupsUseFixedStageOrderAndRoundOnlyOnce() {
        var multipliers = List.of(
                multiplier("test:add_one", "test:add", RuleMultiplierStage.CONTEXT,
                        RuleMultiplierMode.ADD, "0.25", 0),
                multiplier("test:add_two", "test:add", RuleMultiplierStage.CONTEXT,
                        RuleMultiplierMode.ADD, "0.25", 0),
                multiplier("test:low", "test:choice", RuleMultiplierStage.EQUIPMENT,
                        RuleMultiplierMode.HIGHEST, "1.2", 0),
                multiplier("test:high", "test:choice", RuleMultiplierStage.EQUIPMENT,
                        RuleMultiplierMode.HIGHEST, "1.5", 0),
                multiplier("test:lowest", "test:lowest_group", RuleMultiplierStage.PARTY_TEAM,
                        RuleMultiplierMode.LOWEST, "0.8", 0),
                multiplier("test:not_lowest", "test:lowest_group", RuleMultiplierStage.PARTY_TEAM,
                        RuleMultiplierMode.LOWEST, "1.3", 0),
                multiplier("test:replace_low", "test:replace", RuleMultiplierStage.GLOBAL_DIFFICULTY,
                        RuleMultiplierMode.REPLACE, "2.0", 1),
                multiplier("test:replace_high", "test:replace", RuleMultiplierStage.GLOBAL_DIFFICULTY,
                        RuleMultiplierMode.REPLACE, "0.5", 2)
        );
        assertEquals(FixedPoint.parse("90"),
                RuleMultiplierEngine.apply(FixedPoint.parse("100"), multipliers));

        var fractions = List.of(
                multiplier("test:first", "test:first_group", RuleMultiplierStage.CONTEXT,
                        RuleMultiplierMode.MULTIPLY, "1.5", 0),
                multiplier("test:second", "test:second_group", RuleMultiplierStage.EQUIPMENT,
                        RuleMultiplierMode.MULTIPLY, "1.5", 0)
        );
        assertEquals(2, RuleMultiplierEngine.apply(1, fractions));

        assertThrows(IllegalArgumentException.class, () -> RuleMultiplierEngine.apply(
                FixedPoint.parse("10"),
                List.of(
                        multiplier("test:negative_one", "test:negative", RuleMultiplierStage.CONTEXT,
                                RuleMultiplierMode.ADD, "-0.75", 0),
                        multiplier("test:negative_two", "test:negative", RuleMultiplierStage.CONTEXT,
                                RuleMultiplierMode.ADD, "-0.75", 0)
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> RuleMultiplierEngine.apply(
                1,
                List.of(multiplier("test:tiny", "test:tiny", RuleMultiplierStage.CONTEXT,
                        RuleMultiplierMode.MULTIPLY, "0.000001", 0))
        ));
    }

    @Test
    void stackGroupsResolveWithStablePriorityAndDiminishingOrder() {
        RuleDefinition sumHigh = rule("test:sum_high", RuleStackRule.SUM, 20, RuleAntiExploit.defaults());
        RuleDefinition sumLow = rule("test:sum_low", RuleStackRule.SUM, 10, RuleAntiExploit.defaults());
        assertEquals(List.of(sumHigh, sumLow), RuleStackResolver.resolve(List.of(
                new RuleStackResolver.Candidate(sumLow, 10),
                new RuleStackResolver.Candidate(sumHigh, 10)
        )).stream().map(RuleStackResolver.Candidate::rule).toList());

        RuleDefinition high = rule("test:high", RuleStackRule.HIGHEST, 20, RuleAntiExploit.defaults());
        RuleDefinition low = rule("test:low", RuleStackRule.HIGHEST, 10, RuleAntiExploit.defaults());
        var highest = RuleStackResolver.resolve(List.of(
                new RuleStackResolver.Candidate(low, 5),
                new RuleStackResolver.Candidate(high, 10)
        ));
        assertEquals(List.of(high), highest.stream().map(RuleStackResolver.Candidate::rule).toList());

        RuleDefinition firstHigh = rule("test:first_high", RuleStackRule.FIRST, 20,
                RuleAntiExploit.defaults());
        RuleDefinition firstLow = rule("test:first_low", RuleStackRule.FIRST, 10,
                RuleAntiExploit.defaults());
        assertEquals(firstHigh, RuleStackResolver.resolve(List.of(
                new RuleStackResolver.Candidate(firstLow, 100),
                new RuleStackResolver.Candidate(firstHigh, 1)
        )).getFirst().rule());

        RuleDefinition exclusiveHigh = rule("test:exclusive_high", RuleStackRule.EXCLUSIVE, 20,
                RuleAntiExploit.defaults());
        RuleDefinition exclusiveLow = rule("test:exclusive_low", RuleStackRule.EXCLUSIVE, 10,
                RuleAntiExploit.defaults());
        assertEquals(List.of(exclusiveHigh), RuleStackResolver.resolve(List.of(
                new RuleStackResolver.Candidate(exclusiveLow, 100),
                new RuleStackResolver.Candidate(exclusiveHigh, 1)
        )).stream().map(RuleStackResolver.Candidate::rule).toList());

        RuleDefinition diminishHigh = rule("test:diminish_high", RuleStackRule.DIMINISHING, 20,
                RuleAntiExploit.defaults());
        RuleDefinition diminishLow = rule("test:diminish_low", RuleStackRule.DIMINISHING, 10,
                RuleAntiExploit.defaults());
        var diminished = RuleStackResolver.resolve(List.of(
                new RuleStackResolver.Candidate(diminishLow, 10),
                new RuleStackResolver.Candidate(diminishHigh, 10)
        ));
        assertEquals(List.of(10L, 5L), diminished.stream()
                .map(RuleStackResolver.Candidate::amountUnits).toList());
    }

    @Test
    void cooldownRepeatAndRateMemoryCommitAtomicallyWithTheAward() {
        RuleAntiExploit policy = new RuleAntiExploit(
                FakePlayerPolicy.DENY,
                false,
                10,
                FixedPoint.parse("10"),
                FixedPoint.parse("15"),
                FixedPoint.parse("30"),
                100,
                FixedPoint.parse("0.5"),
                FixedPoint.parse("0.25")
        );
        RuleDefinition rule = rule("test:memory", RuleStackRule.SUM, 0, policy);
        RuleMemoryKeys keys = RuleMemoryKeys.forRule(rule.id());
        var service = ProgressionTransactionService.boundedDefaults();

        RuleAntiExploitDecision first = RuleAntiExploitEngine.evaluate(
                rule, keys, service.snapshot(PLAYER), 0, FixedPoint.parse("10")
        );
        assertTrue(first.accepted());
        execute(service, first, "test/rule/first");
        assertEquals(FixedPoint.parse("10"), service.snapshot(PLAYER).balances().get(id("test:awarded")));
        assertTrue(service.snapshot(PLAYER).balances().containsKey(keys.minute().used()));

        assertEquals("rule cooldown is active", RuleAntiExploitEngine.evaluate(
                rule, keys, service.snapshot(PLAYER), 5, FixedPoint.parse("10")
        ).reason());

        RuleAntiExploitDecision second = RuleAntiExploitEngine.evaluate(
                rule, keys, service.snapshot(PLAYER), 10, FixedPoint.parse("10")
        );
        assertEquals(FixedPoint.parse("5"), second.awardedUnits());
        execute(service, second, "test/rule/second");
        assertEquals(FixedPoint.parse("15"), service.snapshot(PLAYER).balances().get(id("test:awarded")));

        RuleAntiExploitDecision capped = RuleAntiExploitEngine.evaluate(
                rule, keys, service.snapshot(PLAYER), 20, FixedPoint.parse("10")
        );
        assertFalse(capped.accepted());
        assertEquals("rule rate cap is exhausted", capped.reason());
    }

    @Test
    void firstTimeMemorySurvivesAStateSnapshotAndCannotRewardAgain() {
        RuleAntiExploit firstOnly = new RuleAntiExploit(
                FakePlayerPolicy.DENY,
                true,
                0,
                0,
                0,
                0,
                0,
                FixedPoint.SCALE,
                FixedPoint.SCALE
        );
        RuleDefinition rule = rule("test:first_only", RuleStackRule.FIRST, 0, firstOnly);
        RuleMemoryKeys keys = RuleMemoryKeys.forRule(rule.id());
        var service = ProgressionTransactionService.boundedDefaults();
        RuleAntiExploitDecision first = RuleAntiExploitEngine.evaluate(
                rule, keys, service.snapshot(PLAYER), 200, FixedPoint.parse("2")
        );
        execute(service, first, "test/rule/only");
        var persisted = service.exportAccount(PLAYER);
        var restored = ProgressionTransactionService.boundedDefaults();
        restored.restoreAccount(PLAYER, persisted);
        assertEquals("first time reward already received", RuleAntiExploitEngine.evaluate(
                rule, keys, restored.snapshot(PLAYER), 400, FixedPoint.parse("2")
        ).reason());
        assertTrue(restored.snapshot(PLAYER).balances().keySet().stream()
                .filter(RuleMemoryKeys::isInternal).count() >= 2);
        assertFalse(RuleMemoryKeys.isInternal(id("progressiveskills:skill_xp/test/value")));
    }

    private static void execute(
            ProgressionTransactionService service,
            RuleAntiExploitDecision decision,
            String key
    ) {
        var balances = new ArrayList<>(decision.memoryMutations());
        balances.add(new BalanceMutation(id("test:awarded"), decision.awardedUnits(), 0, Long.MAX_VALUE));
        var plan = CascadePlan.single(new TransactionPlan(
                PLAYER,
                PLAYER,
                new IdempotencyKey(key),
                service.snapshot(PLAYER).stateRevision(),
                REVISION,
                ProgressionCause.GAMEPLAY,
                "Test rule award",
                new TransactionStep(id("test:rule"), balances, List.of(), List.of())
        ));
        assertTrue(service.execute(plan, REVISION, projector(), executor()).status().committed());
    }

    private static RuleMultiplier multiplier(
            String id,
            String group,
            RuleMultiplierStage stage,
            RuleMultiplierMode mode,
            String value,
            int priority
    ) {
        return new RuleMultiplier(
                id(id), stage, id(group), mode, FixedPoint.parse(value), priority
        );
    }

    private static RuleDefinition rule(
            String id,
            RuleStackRule stackRule,
            int priority,
            RuleAntiExploit policy
    ) {
        return new RuleDefinition(
                id(id),
                true,
                RuleTriggerRegistry.BLOCK_BREAK,
                priority,
                id("test:group"),
                stackRule,
                "actor",
                List.of(new RuleMatcherSpec("id", "minecraft:stone", false)),
                false,
                FixedPoint.parse("10"),
                List.of(),
                policy,
                new RuleDefinition.XpOutput(id("test:output/" + id.substring(id.indexOf(':') + 1)),
                        id("progressiveskills:physique"))
        );
    }

    private static PersistentProjector projector() {
        return new PersistentProjector() {
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
}
