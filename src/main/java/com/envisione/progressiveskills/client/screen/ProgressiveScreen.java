package com.envisione.progressiveskills.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class ProgressiveScreen extends Screen {
    private boolean backgroundRendered;

    protected ProgressiveScreen(Component title) {
        super(title);
    }

    protected final void renderBackgroundLayer(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        backgroundRendered = false;
        renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (backgroundRendered) {
            return;
        }
        backgroundRendered = true;
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
