package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.common.rule.RuleMemoryKeys;
import com.envisione.progressiveskills.common.ability.AbilityProgression;
import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.common.skill.CurveRounding;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCurve;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkRuntimeTest {
    @Test
    void nextLevelProgressUsesAuthoritativeCurveUnits() {
        SkillCurve curve = SkillCurve.linear(
                0,
                3,
                CurveRounding.CEIL,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(25)
        );

        var progress = NetworkRuntime.skillProgress(
                curve,
                1L,
                FixedPoint.parse("150")
        );

        assertEquals(FixedPoint.parse("50"), progress.intoLevelUnits());
        assertEquals(FixedPoint.parse("125"), progress.nextLevelUnits());
        assertEquals(
                new NetworkRuntime.SkillProgressProjection(0L, 0L),
                NetworkRuntime.skillProgress(curve, 3L, curve.capUnits())
        );
    }

    @Test
    void internalRuleAndAbilityMemoryNeverEntersVisiblePlayerBalances() {
        ResourceLocation rule = ResourceLocation.parse("test:block_rule");
        ResourceLocation internal = RuleMemoryKeys.forRule(rule).firstTime();
        ResourceLocation visible = ResourceLocation.parse("progressiveskills:skill_xp/test/value");
        ResourceLocation abilityState = AbilityProgression.cooldownBalanceId(
                ResourceLocation.parse("test:shared_cooldown"));

        Map<String, Long> balances = NetworkRuntime.visibleBalances(Map.of(
                internal, 1L,
                abilityState, 40L,
                visible, 20L
        ));

        assertEquals(20L, balances.get(visible.toString()));
        assertFalse(balances.containsKey(internal.toString()));
        assertFalse(balances.containsKey(abilityState.toString()));
    }

    @Test
    void fixedAbilitySlotsProjectAsZeroBasedIndexesWithSelection() {
        ResourceLocation guard = ResourceLocation.parse("test:guard");
        AbilityState state = new AbilityState(
                Set.of(guard), Map.of(AbilityState.slotId(7), guard),
                Optional.of(AbilityState.slotId(7)), Map.of(), Map.of(), Map.of());

        assertEquals(Map.of(7, guard), NetworkRuntime.visibleAbilitySlots(state));
        assertEquals(7, NetworkRuntime.visibleSelectedAbilitySlot(state));

        AbilityState unsafe = new AbilityState(
                Set.of(), Map.of(AbilityState.slotId(0), guard),
                Optional.of(AbilityState.slotId(0)), Map.of(), Map.of(), Map.of());
        assertEquals(Map.of(), NetworkRuntime.visibleAbilitySlots(unsafe));
        assertEquals(-1, NetworkRuntime.visibleSelectedAbilitySlot(unsafe));
    }
}
