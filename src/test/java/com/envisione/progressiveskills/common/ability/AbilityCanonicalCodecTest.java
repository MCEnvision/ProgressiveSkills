package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbilityCanonicalCodecTest {
    @Test
    void activeAbilityRoundTripsEveryCoreFieldAndAuthoredActionOrder() {
        AbilityDefinition definition = new AbilityDefinition(
                id("test:arcane_burst"),
                presentation("Arcane Burst"),
                true,
                AbilityKind.ACTIVE,
                true,
                false,
                List.of(),
                List.of(
                        new AbilityVanillaCost(id("test:arcane_burst/hunger"), AbilityCostType.HUNGER, 3),
                        new AbilityCurrencyCost(id("test:arcane_burst/points"), id("test:points"), 5),
                        new AbilityVanillaCost(id("test:arcane_burst/xp"), AbilityCostType.EXPERIENCE, 7)
                ),
                new AbilityTargeting(AbilityTargetMode.ENTITY, 24, true),
                id("test:arcane_cooldown"),
                80,
                2,
                200,
                List.of(
                        new AbilityMessageAction(id("test:arcane_burst/z_message"), ComponentSpec.literal("Burst")),
                        new AbilityHealAction(id("test:arcane_burst/a_heal"), 4 * FixedPoint.SCALE),
                        new AbilityVanillaEffectAction(
                                id("test:arcane_burst/m_effect"), id("minecraft:speed"),
                                1, 100, false, true, true
                        )
                )
        );
        var canonical = AbilityCanonicalCodec.encode(
                new DefinitionKey(DefinitionKinds.ABILITY, definition.id()),
                definition,
                new Provenance(id("test:pack"), "abilities/arcane_burst.toml", "toml"),
                SourceMap.empty()
        );

        AbilityDefinition decoded = AbilityCanonicalCodec.decode(canonical);

        assertEquals(definition, decoded);
        assertEquals(
                List.of(id("test:arcane_burst/z_message"), id("test:arcane_burst/a_heal"),
                        id("test:arcane_burst/m_effect")),
                decoded.actions().stream().map(AbilityAction::id).toList()
        );
    }

    @Test
    void persistentEffectsExposeStableSourceOwnedContributions() {
        AbilityAttributeEffect attribute = new AbilityAttributeEffect(
                id("test:guard/armor"), id("minecraft:generic.armor"),
                AttributeOperation.ADD_VALUE, 2 * FixedPoint.SCALE
        );
        AbilityFlagEffect flag = new AbilityFlagEffect(
                id("test:guard/flag"), id("test:guarding"), false
        );
        AbilityDefinition toggle = new AbilityDefinition(
                id("test:guard"), presentation("Guard"), true, AbilityKind.TOGGLE,
                true, true, List.of(flag, attribute), List.of(), AbilityTargeting.SELF,
                id("test:guard"), 0, 1, 0, List.of()
        );

        assertEquals(id("progressiveskills:ability"), attribute.source(toggle.id()).ownerKind());
        assertEquals(toggle.id(), attribute.source(toggle.id()).ownerId());
        assertEquals(EntitlementResolver.ADDITIVE, attribute.contribution().resolver());
        assertEquals(EntitlementResolver.HIGHEST, flag.contribution().resolver());
        assertEquals(0, flag.contribution().value());
        assertFalse(toggle.persistentEffects().isEmpty());
    }

    @Test
    void lifecycleTargetChargeAndCostInvariantsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityTargeting(
                AbilityTargetMode.SELF, 1, false
        ));
        assertThrows(IllegalArgumentException.class, () -> new AbilityTargeting(
                AbilityTargetMode.BLOCK, 65, true
        ));
        assertThrows(IllegalArgumentException.class, () -> active(
                List.of(), 2, 0, List.of(new AbilityHealAction(id("test:a/heal"), FixedPoint.SCALE))
        ));
        assertThrows(IllegalArgumentException.class, () -> new AbilityDefinition(
                id("test:unslotted_active"), presentation("Unslotted active"), true, AbilityKind.ACTIVE,
                false, false, List.of(), List.of(), AbilityTargeting.SELF,
                id("test:unslotted_active"), 0, 1, 0,
                List.of(new AbilityMessageAction(
                        id("test:unslotted_active/message"), ComponentSpec.literal("Active")))
        ));
        assertThrows(IllegalArgumentException.class, () -> new AbilityDefinition(
                id("test:bad_passive"), presentation("Bad"), true, AbilityKind.PASSIVE,
                true, false,
                List.of(new AbilityFlagEffect(id("test:bad_passive/flag"), id("test:flag"), true)),
                List.of(), AbilityTargeting.SELF, id("test:bad_passive"), 0, 1, 0, List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> active(
                List.of(
                        new AbilityVanillaCost(id("test:a/hunger"), AbilityCostType.HUNGER, 1),
                        new AbilityVanillaCost(id("test:a/hunger"), AbilityCostType.HUNGER, 2)
                ),
                1,
                0,
                List.of(new AbilityHealAction(id("test:a/heal"), FixedPoint.SCALE))
        ));
        assertThrows(IllegalArgumentException.class, () -> active(List.of(), 1, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AbilityDefinition(
                id("test:block_heal"), presentation("Block heal"), true, AbilityKind.ACTIVE,
                true, false, List.of(), List.of(),
                new AbilityTargeting(AbilityTargetMode.BLOCK, 8, true),
                id("test:block_heal"), 0, 1, 0,
                List.of(new AbilityHealAction(id("test:block_heal/heal"), FixedPoint.SCALE))
        ));
    }

    private static AbilityDefinition active(
            List<AbilityCost> costs,
            int charges,
            int recharge,
            List<AbilityAction> actions
    ) {
        return new AbilityDefinition(
                id("test:a"), presentation("A"), true, AbilityKind.ACTIVE, true, false,
                List.of(), costs, AbilityTargeting.SELF, id("test:a"), 0,
                charges, recharge, actions
        );
    }

    private static DefinitionPresentation presentation(String fallback) {
        ResourceLocation barrier = id("minecraft:barrier");
        return new DefinitionPresentation(
                ComponentSpec.literal(fallback),
                Optional.empty(),
                IconSpec.single(IconKind.ITEM, barrier, barrier, ComponentSpec.literal(fallback)),
                Set.of()
        );
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
