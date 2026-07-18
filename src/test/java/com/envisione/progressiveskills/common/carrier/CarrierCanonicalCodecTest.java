package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconKind;
import com.envisione.progressiveskills.common.presentation.IconSpec;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.source.Provenance;
import com.envisione.progressiveskills.common.source.SourceMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarrierCanonicalCodecTest {
    @Test
    void canonicalRoundTripPreservesPresentationBehaviorAndAuthoredActionOrder() {
        CarrierDefinition definition = definition();
        DefinitionKey key = new DefinitionKey(DefinitionKinds.ITEM, definition.id());

        CarrierDefinition decoded = CarrierCanonicalCodec.decode(CarrierCanonicalCodec.encode(
                key,
                definition,
                new Provenance(id("test:pack"), "items/training.toml", "toml"),
                SourceMap.empty()
        ));

        assertEquals(definition, decoded);
        assertEquals(
                List.of(
                        id("test:training/z_xp"),
                        id("test:training/a_level"),
                        id("test:training/m_currency"),
                        id("test:training/b_respec")
                ),
                decoded.useActions().stream().map(CarrierUseAction::id).toList()
        );
    }

    static CarrierDefinition definition() {
        return new CarrierDefinition(
                id("test:training"),
                new DefinitionPresentation(
                        ComponentSpec.literal("Training carrier"),
                        Optional.of(ComponentSpec.literal("A deterministic test carrier")),
                        IconSpec.single(
                                IconKind.ITEM,
                                id("minecraft:book"),
                                id("minecraft:barrier"),
                                ComponentSpec.literal("Book")
                        ),
                        Set.of("training")
                ),
                true,
                CarrierKind.TOME,
                7,
                CarrierMigrationPolicy.KEEP_PINNED,
                CarrierBindPolicy.ON_USE,
                CarrierDeliveryPolicy.PENDING_CLAIM,
                CarrierRarity.RARE,
                true,
                16,
                5,
                20,
                List.of(
                        new CarrierSkillXpAction(
                                id("test:training/z_xp"), id("test:physique"),
                                FixedPoint.wholeXpToUnits(25), 1
                        ),
                        new CarrierSkillLevelAction(
                                id("test:training/a_level"), id("test:physique"), 1, 1
                        ),
                        new CarrierCurrencyAction(
                                id("test:training/m_currency"), id("test:points"), 3, 1
                        ),
                        new CarrierTreeRespecAction(
                                id("test:training/b_respec"), id("test:tree"), 1
                        )
                )
        );
    }

    static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
