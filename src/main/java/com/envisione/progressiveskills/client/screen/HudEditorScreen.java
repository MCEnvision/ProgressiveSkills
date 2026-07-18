package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ClientPreferences;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen {
    public HudEditorScreen() {
        super(Component.literal("Progression HUD Editor"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = Math.max(36, height / 2 - 70);
        addRenderableWidget(Button.builder(Component.literal(
                        "Anchor " + ClientPreferences.hudAnchor().serializedName()), ignored -> update(
                        ClientPreferences::cycleHudAnchor))
                .bounds(center - 100, top, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Left"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(-4, 0)))
                .bounds(center - 100, top + 24, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Right"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(4, 0)))
                .bounds(center - 48, top + 24, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(0, -4)))
                .bounds(center + 4, top + 24, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(0, 4)))
                .bounds(center + 56, top + 24, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal(
                        "Scale " + ClientPreferences.hudScale() + " percent"), ignored -> update(
                        ClientPreferences::cycleHudScale))
                .bounds(center - 100, top + 48, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal(
                        "Opacity " + ClientPreferences.hudOpacity() + " percent"), ignored -> update(
                        ClientPreferences::cycleHudOpacity))
                .bounds(center + 2, top + 48, 102, 20).build());
        addRenderableWidget(Button.builder(Component.literal(
                        "HUD " + (ClientPreferences.hudEnabled() ? "shown" : "hidden")), ignored -> update(
                        ClientPreferences::toggleHud))
                .bounds(center - 100, top + 72, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset layout"), ignored -> update(
                        ClientPreferences::resetHudLayout))
                .bounds(center + 2, top + 72, 102, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(center - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(6, 6, width - 6, height - 6, 0xE8181818);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        String position = "Offset " + ClientPreferences.hudOffsetX() + ", "
                + ClientPreferences.hudOffsetY() + ".";
        graphics.drawCenteredString(font, Component.literal(position), width / 2, 24, 0xB8D8F8);
        renderPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(title.getString() + ". Anchor "
                + ClientPreferences.hudAnchor().serializedName() + ". Offset "
                + ClientPreferences.hudOffsetX() + ", " + ClientPreferences.hudOffsetY()
                + ". Scale " + ClientPreferences.hudScale() + " percent. Opacity "
                + ClientPreferences.hudOpacity() + " percent.");
    }

    private void renderPreview(GuiGraphics graphics) {
        String text = "Slot 1. Preview ability";
        float scale = ClientPreferences.hudScale() / 100.0F;
        int panelWidth = font.width(text) + 12;
        int left = Math.round((width / 2.0F - panelWidth * scale / 2.0F) / scale);
        int top = Math.round(42 / scale);
        int alpha = Math.round(255.0F * ClientPreferences.hudOpacity() / 100.0F);
        int color = alpha << 24 | (ClientPreferences.highContrast() ? 0x000000 : 0x202020);
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.fill(left, top, left + panelWidth, top + 18, color);
        graphics.drawString(font, Component.literal(text), left + 6, top + 5, 0xFFFFFF, false);
        graphics.pose().popPose();
    }

    private void update(Runnable change) {
        change.run();
        rebuildWidgets();
    }
}
