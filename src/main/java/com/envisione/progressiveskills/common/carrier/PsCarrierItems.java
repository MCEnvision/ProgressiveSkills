package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.ProjectIdentity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public final class PsCarrierItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectIdentity.MOD_ID);

    public static final DeferredItem<Item> TOME = register(CarrierKind.TOME);
    public static final DeferredItem<Item> TOKEN = register(CarrierKind.TOKEN);
    public static final DeferredItem<Item> CHARM = register(CarrierKind.CHARM);
    public static final DeferredItem<Item> CONSUMABLE = register(CarrierKind.CONSUMABLE);
    public static final DeferredItem<Item> ARTIFACT = register(CarrierKind.ARTIFACT);

    private PsCarrierItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static Item item(CarrierKind kind) {
        return switch (kind) {
            case TOME -> TOME.get();
            case TOKEN -> TOKEN.get();
            case CHARM -> CHARM.get();
            case CONSUMABLE -> CONSUMABLE.get();
            case ARTIFACT -> ARTIFACT.get();
        };
    }

    public static Optional<CarrierKind> kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        for (CarrierKind kind : CarrierKind.values()) {
            if (item == item(kind)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private static DeferredItem<Item> register(CarrierKind kind) {
        return ITEMS.registerSimpleItem(kind.serializedName(), new Item.Properties().stacksTo(64));
    }
}
