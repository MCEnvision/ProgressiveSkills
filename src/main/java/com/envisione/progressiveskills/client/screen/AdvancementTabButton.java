package com.envisione.progressiveskills.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

final class AdvancementTabButton extends AbstractButton {
    private final ItemStack icon;
    private final Runnable operation;
    private final boolean selected;
    private final Side side;
    private final Position position;

    AdvancementTabButton(
            int x,
            int y,
            Component message,
            ItemStack icon,
            boolean selected,
            Side side,
            Position position,
            Runnable operation
    ) {
        super(x, y, side.horizontal() ? 28 : 32, side.horizontal() ? 32 : 28, message);
        this.icon = Objects.requireNonNull(icon, "icon");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.selected = selected;
        this.side = side;
        this.position = position;
        setTooltip(Tooltip.create(message));
    }

    @Override
    public void onPress() {
        if (!selected) {
            operation.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String location = "advancements/tab_" + side.serialized + "_" + position.serialized(side)
                + (selected ? "_selected" : "");
        graphics.blitSprite(ResourceLocation.withDefaultNamespace(location), getX(), getY(), width, height);
        int iconX = getX() + (side == Side.LEFT ? 10 : 6);
        int iconY = getY() + (side == Side.ABOVE ? 9 : 6);
        graphics.renderItem(icon, iconX, iconY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (selected) {
            output.add(NarratedElementType.USAGE,
                    Component.translatable("screen.progressiveskills.tab.selected"));
        }
    }

    enum Side {
        ABOVE("above"),
        BELOW("below"),
        LEFT("left"),
        RIGHT("right");

        private final String serialized;

        Side(String serialized) {
            this.serialized = serialized;
        }

        private boolean horizontal() {
            return this == ABOVE || this == BELOW;
        }
    }

    enum Position {
        FIRST("left", "top"),
        MIDDLE("middle", "middle"),
        LAST("right", "bottom");

        private final String above;
        private final String left;

        Position(String above, String left) {
            this.above = above;
            this.left = left;
        }

        private String serialized(Side side) {
            return side.horizontal() ? above : left;
        }

    }
}
