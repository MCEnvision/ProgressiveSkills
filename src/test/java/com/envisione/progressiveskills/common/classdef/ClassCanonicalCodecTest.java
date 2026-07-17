package com.envisione.progressiveskills.common.classdef;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassCanonicalCodecTest {
    @Test
    void classAndSlotRoundTripEveryCoreField() {
        ClassSlotDefinition slot = new ClassSlotDefinition(
                id("test:combat"), presentation("Combat"), 2, ClassSwapPolicy.ALLOWED
        );
        ClassSynergyDefinition synergy = new ClassSynergyDefinition(
                id("test:spellblade"),
                presentation("Spellblade"),
                true,
                List.of(id("test:warrior"), id("test:mage")),
                List.of(new ClassEntitlementGrant(
                        id("test:spellblade/stance"), ClassGrantType.ABILITY, id("test:stance"), 1
                ))
        );
        ClassDefinition definition = new ClassDefinition(
                id("test:mage"),
                presentation("Mage"),
                true,
                true,
                slot.id(),
                1,
                Set.of(id("test:arcane")),
                Map.of(id("test:arcana"), 15),
                List.of(id("test:tree/root")),
                List.of(id("test:warrior")),
                Optional.of(new ClassCurrencyCost(id("test:points"), 5)),
                true,
                Optional.of(new ClassCurrencyCost(id("test:points"), 2)),
                Optional.of(new ClassStarterKit(
                        ClassDefinition.starterKitReceiptId(id("test:mage")),
                        List.of(id("minecraft:book"), id("minecraft:stick"))
                )),
                List.of(
                        new ClassAttributeGrant(
                                id("test:mage/scale"), id("minecraft:generic.scale"),
                                AttributeOperation.ADD_VALUE, -100_000
                        ),
                        new ClassSpellGrant(
                                id("test:mage/fireball"), id("test:fireball"), 3,
                                ClassSpellLearningPolicy.SATISFY_WHILE_OWNED
                        ),
                        new ClassEntitlementGrant(
                                id("test:mage/tree"), ClassGrantType.TREE_ACCESS, id("test:tree"), 1
                        )
                ),
                List.of(synergy)
        );
        Provenance provenance = new Provenance(id("test:pack"), "classes/mage.toml", "toml");

        var canonicalSlot = ClassCanonicalCodec.encodeSlot(
                new DefinitionKey(DefinitionKinds.CLASS_SLOT, slot.id()),
                slot,
                provenance,
                SourceMap.empty()
        );
        var canonicalClass = ClassCanonicalCodec.encodeClass(
                new DefinitionKey(DefinitionKinds.CLASS, definition.id()),
                definition,
                provenance,
                SourceMap.empty()
        );

        assertEquals(slot, ClassCanonicalCodec.decodeSlot(canonicalSlot));
        assertEquals(definition, ClassCanonicalCodec.decodeClass(canonicalClass));
        ClassSpellGrant decodedSpell = (ClassSpellGrant) ClassCanonicalCodec.decodeClass(canonicalClass)
                .grants().stream().filter(grant -> grant.type() == ClassGrantType.SPELL).findFirst().orElseThrow();
        assertEquals(ClassSpellLearningPolicy.SATISFY_WHILE_OWNED, decodedSpell.learningPolicy());
    }

    @Test
    void grantsExposeTypedSourceAwareContributions() {
        ClassGrant ability = new ClassEntitlementGrant(
                id("test:warrior/guard"), ClassGrantType.ABILITY, id("test:guard"), 1
        );
        ClassGrant spell = new ClassSpellGrant(
                id("test:mage/fireball"), id("test:fireball"), 4,
                ClassSpellLearningPolicy.REQUIRE_EXISTING
        );

        assertEquals(EntitlementResolver.BOOLEAN_UNION, ability.resolver());
        assertEquals(1, ability.contribution().value());
        assertEquals(EntitlementResolver.HIGHEST, spell.resolver());
        assertEquals(4, spell.contribution().value());
        assertEquals(id("test:warrior"), ability.source(DefinitionKinds.CLASS.id(), id("test:warrior")).ownerId());
        assertThrows(IllegalArgumentException.class, () -> new ClassEntitlementGrant(
                id("test:bad"), ClassGrantType.ABILITY, id("test:guard"), 2
        ));
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
