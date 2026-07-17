package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CurrencyDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        long minimum,
        long maximum,
        long initial,
        String scope
) {
    public CurrencyDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        scope = Objects.requireNonNull(scope, "scope");
        if (!scope.equals("character")) {
            throw new IllegalArgumentException("Phase 7 currencies require character scope");
        }
        if (minimum > maximum || initial < minimum || initial > maximum) {
            throw new IllegalArgumentException("Currency bounds and initial value are inconsistent");
        }
    }
}
