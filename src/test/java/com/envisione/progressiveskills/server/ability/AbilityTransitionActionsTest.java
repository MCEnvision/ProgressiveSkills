package com.envisione.progressiveskills.server.ability;

import com.envisione.progressiveskills.common.ability.AbilityCostType;
import com.envisione.progressiveskills.common.ability.AbilityHealAction;
import com.envisione.progressiveskills.common.ability.AbilityVanillaCost;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbilityTransitionActionsTest {
    private static final ResourceLocation ABILITY = id("guard");
    private static final ResourceLocation MEMBER = id("guard/heal");

    @Test
    void roundTripsCanonicalVanillaCostReference() {
        var transition = AbilityTransitionActions.cost(
                ABILITY,
                new AbilityVanillaCost(MEMBER, AbilityCostType.HUNGER, 3)
        );

        var decoded = AbilityTransitionActions.decodeCost(transition);

        assertEquals(ABILITY, decoded.abilityId());
        assertEquals(MEMBER, decoded.memberId());
        assertEquals(3, transition.amount());
        assertEquals(AbilityExecutionTarget.self(), decoded.target());
    }

    @Test
    void roundTripsEveryTargetShape() {
        var action = new AbilityHealAction(MEMBER, 250);
        var entity = AbilityExecutionTarget.entity(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        var block = AbilityExecutionTarget.block(new BlockPos(-4, 70, 9));

        assertEquals(
                AbilityExecutionTarget.self(),
                AbilityTransitionActions.decodeAction(
                        AbilityTransitionActions.action(ABILITY, action, AbilityExecutionTarget.self())
                ).target()
        );
        assertEquals(
                entity,
                AbilityTransitionActions.decodeAction(
                        AbilityTransitionActions.action(ABILITY, action, entity)
                ).target()
        );
        assertEquals(
                block,
                AbilityTransitionActions.decodeAction(
                        AbilityTransitionActions.action(ABILITY, action, block)
                ).target()
        );
    }

    @Test
    void rejectsNonCanonicalOrWrongTransitionShapes() {
        var transition = AbilityTransitionActions.action(
                ABILITY,
                new AbilityHealAction(MEMBER, 100),
                AbilityExecutionTarget.self()
        );

        assertThrows(IllegalArgumentException.class, () -> AbilityTransitionActions.decodeCost(transition));
        assertThrows(IllegalArgumentException.class, () -> new AbilityExecutionTarget(
                com.envisione.progressiveskills.common.ability.AbilityTargetMode.ENTITY,
                java.util.Optional.empty(),
                java.util.Optional.empty()
        ));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
