package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ClientPreferences;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends ProgressiveScreen {
    public HudEditorScreen() {
        super(Component.literal("Progression HUD Editor"));
    }

    @Override
    protected void init() {
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        int left = frame.contentX() + 4;
        int top = frame.contentY() + 27;
        addRenderableWidget(Button.builder(Component.literal(
                        "Anchor " + ClientPreferences.hudAnchor().serializedName()), ignored -> update(
                        ClientPreferences::cycleHudAnchor))
                .bounds(left, top, 226, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Left"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(-4, 0)))
                .bounds(left, top + 21, 55, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Right"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(4, 0)))
                .bounds(left + 57, top + 21, 55, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(0, -4)))
                .bounds(left + 114, top + 21, 55, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), ignored -> update(
                        () -> ClientPreferences.nudgeHud(0, 4)))
                .bounds(left + 171, top + 21, 55, 19).build());
        addRenderableWidget(Button.builder(Component.literal(
                        "Scale " + ClientPreferences.hudScale() + " percent"), ignored -> update(
                        ClientPreferences::cycleHudScale))
                .bounds(left, top + 42, 112, 19).build());
        addRenderableWidget(Button.builder(Component.literal(
                        "Opacity " + ClientPreferences.hudOpacity() + " percent"), ignored -> update(
                        ClientPreferences::cycleHudOpacity))
                .bounds(left + 114, top + 42, 112, 19).build());
        addRenderableWidget(Button.builder(Component.literal(
                        "HUD " + (ClientPreferences.hudEnabled() ? "shown" : "hidden")), ignored -> update(
                        ClientPreferences::toggleHud))
                .bounds(left, top + 63, 112, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Reset layout"), ignored -> update(
                        ClientPreferences::resetHudLayout))
                .bounds(left + 114, top + 63, 112, 19).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(frame.x() + 76, frame.footerY(), 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        AdvancementUi.renderInside(graphics, frame);
        AdvancementUi.renderWindow(graphics, font, frame, title);
        String position = "Offset " + ClientPreferences.hudOffsetX() + ", "
                + ClientPreferences.hudOffsetY() + ".";
        graphics.drawCenteredString(font, Component.literal(position), width / 2,
                frame.contentY() + 16, 0xB8D8F8);
        renderPreview(graphics, frame);
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

    private void renderPreview(GuiGraphics graphics, AdvancementUi.Frame frame) {
        String text = "Slot 1. Preview ability";
        float scale = ClientPreferences.hudScale() / 100.0F;
        int panelWidth = font.width(text) + 12;
        int left = Math.round((width / 2.0F - panelWidth * scale / 2.0F) / scale);
        int top = Math.round((frame.contentY() + 2) / scale);
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
