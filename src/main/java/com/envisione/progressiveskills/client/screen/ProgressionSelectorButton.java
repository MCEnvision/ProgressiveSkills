package com.envisione.progressiveskills.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

final class ProgressionSelectorButton extends AbstractButton {
    private final ItemStack icon;
    private final Component summary;
    private final String badge;
    private final boolean selected;
    private final Runnable operation;
    private final int entryIndex;

    ProgressionSelectorButton(
            int x,
            int y,
            int width,
            int height,
            int entryIndex,
            Component message,
            Component summary,
            ItemStack icon,
            String badge,
            boolean selected,
            Runnable operation
    ) {
        super(x, y, width, height, message);
        this.entryIndex = entryIndex;
        this.summary = Objects.requireNonNull(summary, "summary");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.badge = Objects.requireNonNull(badge, "badge");
        this.selected = selected;
        this.operation = Objects.requireNonNull(operation, "operation");
        setTooltip(Tooltip.create(Component.empty().append(message).append("\n").append(summary)));
    }

    @Override
    public void onPress() {
        if (!selected) {
            operation.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int background = selected ? 0xE05B4636 : isHoveredOrFocused() ? 0xE04A4A4A : 0xD02A2A2A;
        int border = selected ? 0xFFFFC55C : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF777777;
        graphics.fill(getX(), getY(), getRight(), getBottom(), background);
        graphics.hLine(getX(), getRight() - 1, getY(), border);
        graphics.vLine(getX(), getY(), getBottom() - 1, border);
        graphics.hLine(getX(), getRight() - 1, getBottom() - 1, 0xFF151515);
        graphics.vLine(getRight() - 1, getY(), getBottom() - 1, 0xFF151515);
        boolean showLabel = width >= 88;
        int iconX = showLabel ? getX() + 5 : getX() + (width - 16) / 2;
        int iconY = getY() + (height - 16) / 2;
        graphics.renderItem(icon, iconX, iconY);
        var font = Minecraft.getInstance().font;
        if (showLabel) {
            int textX = getX() + 25;
            int textWidth = Math.max(8, width - 29 - font.width(badge));
            graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), textWidth),
                    textX, getY() + (height - font.lineHeight) / 2, 0xFFFFFFFF, false);
        }
        if (!badge.isEmpty()) {
            int badgeWidth = font.width(badge) + 4;
            int badgeX = getRight() - badgeWidth - 2;
            int badgeY = getBottom() - font.lineHeight - 2;
            graphics.fill(badgeX, badgeY, getRight() - 1, getBottom() - 1, 0xE0101010);
            graphics.drawString(font, badge, badgeX + 2, badgeY, selected ? 0xFFFFD65C : 0xFFFFFFFF, true);
        }
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

    int entryIndex() {
        return entryIndex;
    }
}
