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
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillCatalogReservedBalanceTest {
    @Test
    void reservedAbilityStateCurrencyIdsFailCatalogStaging() {
        List.of("cooldown", "charges", "recharge").forEach(
                SkillCatalogReservedBalanceTest::assertReservedCurrency);
    }

    private static void assertReservedCurrency(String category) {
        ResourceLocation currencyId = ResourceLocation.parse(
                "progressiveskills:ability_state/" + category + "/" + "a".repeat(64));
        ComponentSpec text = ComponentSpec.literal("Reserved");
        CurrencyDefinition currency = new CurrencyDefinition(
                currencyId,
                new DefinitionPresentation(
                        text,
                        Optional.empty(),
                        IconSpec.single(
                                IconKind.ITEM,
                                ResourceLocation.parse("minecraft:emerald"),
                                ResourceLocation.parse("minecraft:barrier"),
                                text
                        ),
                        Set.of()
                ),
                0,
                100,
                0,
                "character"
        );
        var canonical = SkillCanonicalCodec.encode(
                new DefinitionKey(DefinitionKinds.CURRENCY, currencyId),
                currency,
                new Provenance(ResourceLocation.parse("progressiveskills:test"), "memory", "test"),
                SourceMap.empty()
        );
        CanonicalIr ir = CanonicalIr.of(List.of(canonical), AliasMap.empty());

        IllegalArgumentException rejection = assertThrows(
                IllegalArgumentException.class,
                () -> SkillCatalog.from(ir)
        );
        assertTrue(rejection.getMessage().contains("Currency id is reserved for internal state"));
    }
}
