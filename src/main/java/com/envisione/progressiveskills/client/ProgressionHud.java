package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.network.PsNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID, value = Dist.CLIENT)
public final class ProgressionHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(
            ProjectIdentity.MOD_ID, "progression_hud");

    private ProgressionHud() {
    }

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, ProgressionHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        AbilityWheelOverlay.render(graphics);
        if (!ClientPreferences.hudEnabled() || AbilityWheelOverlay.isActive()) {
            return;
        }
        var state = PsNetworking.clientSnapshot().visibleState();
        if (state.isEmpty()) {
            return;
        }
        int slot = state.orElseThrow().selectedAbilitySlot();
        Component ability = slot < 0
                ? Component.literal("No ability selected")
                : Component.literal("Slot " + (slot + 1) + ". "
                + state.orElseThrow().abilitySlots().getOrDefault(slot,
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "empty")).getPath());
        float scale = ClientPreferences.hudScale() / 100.0F;
        int scaledWidth = Math.round(graphics.guiWidth() / scale);
        int scaledHeight = Math.round(graphics.guiHeight() / scale);
        int width = minecraft.font.width(ability) + 12;
        boolean right = ClientPreferences.hudAnchor() == ClientPreferences.HudAnchor.UPPER_RIGHT
                || ClientPreferences.hudAnchor() == ClientPreferences.HudAnchor.LOWER_RIGHT;
        boolean lower = ClientPreferences.hudAnchor() == ClientPreferences.HudAnchor.LOWER_LEFT
                || ClientPreferences.hudAnchor() == ClientPreferences.HudAnchor.LOWER_RIGHT;
        int x = (right ? scaledWidth - width - 6 : 6) + ClientPreferences.hudOffsetX();
        int y = (lower ? scaledHeight - 46 : 6) + ClientPreferences.hudOffsetY();
        x = Math.clamp(x, 0, Math.max(0, scaledWidth - width));
        y = Math.clamp(y, 0, Math.max(0, scaledHeight - 18));
        int alpha = Math.round(255.0F * ClientPreferences.hudOpacity() / 100.0F);
        int color = alpha << 24 | (ClientPreferences.highContrast() ? 0x000000 : 0x202020);
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.fill(x, y, x + width, y + 18, color);
        graphics.drawString(minecraft.font, ability, x + 6, y + 5, 0xFFFFFF, false);
        graphics.pose().popPose();
    }
}
