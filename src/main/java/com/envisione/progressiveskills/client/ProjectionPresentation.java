package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.common.network.DefinitionProjection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ProjectionPresentation {
    private ProjectionPresentation() {
    }

    public static Component display(DefinitionProjection.Entry entry, String fallback) {
        return entry.display().map(ProjectionPresentation::component)
                .orElseGet(() -> Component.literal(fallback));
    }

    public static Component component(DefinitionProjection.Text text) {
        return text.localizationKey().map(key -> Component.translatableWithFallback(key, text.fallback()))
                .orElseGet(() -> Component.literal(text.fallback()));
    }

    public static ItemStack icon(DefinitionProjection.Entry entry, Item fallback) {
        return entry.icon().map(icon -> icon(icon, fallback))
                .orElseGet(() -> new ItemStack(fallback));
    }

    public static ItemStack icon(DefinitionProjection.Icon icon, Item fallback) {
        if (!icon.kind().equals("item")) {
            return new ItemStack(fallback);
        }
        return icon.references().stream()
                .filter(BuiltInRegistries.ITEM::containsKey)
                .findFirst()
                .or(() -> BuiltInRegistries.ITEM.containsKey(icon.fallback())
                        ? java.util.Optional.of(icon.fallback()) : java.util.Optional.empty())
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(fallback));
    }
}
