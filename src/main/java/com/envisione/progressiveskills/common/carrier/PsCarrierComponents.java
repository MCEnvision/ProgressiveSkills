package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.ProjectIdentity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PsCarrierComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectIdentity.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CarrierIdentity>> IDENTITY =
            COMPONENTS.registerComponentType("carrier_identity", builder -> builder
                    .persistent(CarrierIdentity.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CarrierIdentity.CODEC))
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CarrierStackState>> STATE =
            COMPONENTS.registerComponentType("carrier_state", builder -> builder
                    .persistent(CarrierStackState.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(CarrierStackState.CODEC))
                    .cacheEncoding());

    private PsCarrierComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
