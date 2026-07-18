package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.SafeRetryTray;
import com.envisione.progressiveskills.common.network.PsNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AbilityWheelScreen extends Screen {
    public AbilityWheelScreen() {
        super(Component.translatable("screen.progressiveskills.ability_wheel.title"));
    }

    @Override
    protected void init() {
        var state = PsNetworking.clientSnapshot().visibleState();
        if (state.isEmpty() || state.orElseThrow().abilitySlots().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(width / 2 - 50, height / 2, 100, 20).build());
            return;
        }
        int centerX = width / 2;
        int centerY = height / 2;
        state.orElseThrow().abilitySlots().forEach((slot, ability) -> {
            double angle = Math.PI * 2.0D * slot / 8.0D - Math.PI / 2.0D;
            int x = centerX + (int) Math.round(Math.cos(angle) * 92.0D) - 55;
            int y = centerY + (int) Math.round(Math.sin(angle) * 62.0D) - 10;
            var visible = state.orElseThrow().abilities().get(ability);
            String suffix = visible == null ? "" : "  " + visible.charges() + "  "
                    + visible.cooldownRemainingTicks();
            Button button = addRenderableWidget(Button.builder(
                    Component.literal((slot + 1) + ". " + ability.getPath() + suffix), ignored -> activate(slot))
                    .bounds(x, y, 110, 20).build());
            button.active = visible != null && visible.ready();
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(6, 6, width - 6, height - 6, 0xD8101010);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("screen.progressiveskills.ability_wheel.hint"),
                width / 2, height - 20, 0xB8B8B8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void activate(int slot) {
        if (SafeRetryTray.sendOrRemember(
                "Activate ability slot " + (slot + 1),
                () -> PsNetworking.sendAbilityActivate(slot))) {
            onClose();
        }
    }
}
