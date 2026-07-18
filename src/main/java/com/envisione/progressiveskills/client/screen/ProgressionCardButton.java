package com.envisione.progressiveskills.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

final class ProgressionCardButton extends AbstractButton {
    private final ItemStack icon;
    private final Component summary;
    private final boolean selected;
    private final Runnable operation;
    private final int rowIndex;

    ProgressionCardButton(
            int x,
            int y,
            int width,
            int height,
            int rowIndex,
            Component message,
            Component summary,
            ItemStack icon,
            boolean selected,
            Runnable operation
    ) {
        super(x, y, width, height, message);
        this.rowIndex = rowIndex;
        this.summary = Objects.requireNonNull(summary, "summary");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.selected = selected;
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    @Override
    public void onPress() {
        if (!selected) {
            operation.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int background = selected ? 0xE05B4636 : isHoveredOrFocused() ? 0xE0474747 : 0xD0282828;
        int border = selected ? 0xFFFFC55C : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF777777;
        graphics.fill(getX(), getY(), getRight(), getBottom(), background);
        graphics.hLine(getX(), getRight() - 1, getY(), border);
        graphics.vLine(getX(), getY(), getBottom() - 1, border);
        graphics.hLine(getX(), getRight() - 1, getBottom() - 1, 0xFF151515);
        graphics.vLine(getRight() - 1, getY(), getBottom() - 1, 0xFF151515);
        graphics.renderItem(icon, getX() + 6, getY() + (height - 16) / 2);
        var font = Minecraft.getInstance().font;
        int textX = getX() + 27;
        int textWidth = Math.max(12, width - 32);
        String name = font.plainSubstrByWidth(getMessage().getString(), textWidth);
        String detail = font.plainSubstrByWidth(summary.getString(), textWidth);
        graphics.drawString(font, name, textX, getY() + 7, 0xFFFFFFFF, false);
        graphics.drawString(font, detail, textX, getY() + height - 14,
                selected ? 0xFFFFD88A : 0xFFB8B8B8, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, summary);
        if (selected) {
            output.add(NarratedElementType.USAGE,
                    Component.translatable("screen.progressiveskills.tab.selected"));
        }
    }

    int rowIndex() {
        return rowIndex;
    }
}
