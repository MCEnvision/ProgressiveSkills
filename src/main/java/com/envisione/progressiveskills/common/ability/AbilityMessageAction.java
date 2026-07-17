package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AbilityMessageAction(
        ResourceLocation id,
        ComponentSpec message
) implements AbilityAction {
    public AbilityMessageAction {
        id = StableId.requireValid(id);
        Objects.requireNonNull(message, "message");
    }

    @Override
    public AbilityActionType type() {
        return AbilityActionType.MESSAGE;
    }
}
