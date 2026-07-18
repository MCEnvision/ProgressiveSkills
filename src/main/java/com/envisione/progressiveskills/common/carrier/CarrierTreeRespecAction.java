package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

public record CarrierTreeRespecAction(
        ResourceLocation id,
        ResourceLocation tree,
        int consume
) implements CarrierUseAction {
    public CarrierTreeRespecAction {
        id = StableId.requireValid(id);
        tree = StableId.requireValid(tree);
        CarrierUseAction.requireConsume(consume);
    }

    @Override
    public CarrierUseActionType type() {
        return CarrierUseActionType.TREE_RESPEC;
    }
}
