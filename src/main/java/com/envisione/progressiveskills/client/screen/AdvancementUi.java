package com.envisione.progressiveskills.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class AdvancementUi {
    static final int WINDOW_WIDTH = 252;
    static final int WINDOW_HEIGHT = 140;
    private static final int MAX_LARGE_WIDTH = 520;
    private static final int MAX_LARGE_HEIGHT = 330;

    private static final ResourceLocation WINDOW = ResourceLocation.withDefaultNamespace(
            "textures/gui/advancements/window.png");
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace(
            "textures/gui/advancements/backgrounds/stone.png");

    private AdvancementUi() {
    }

    static Frame frame(int screenWidth, int screenHeight) {
        int x = (screenWidth - WINDOW_WIDTH) / 2;
        int y = Math.max(34, (screenHeight - WINDOW_HEIGHT - 28) / 2);
        return new Frame(x, y, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    static Frame largeFrame(int screenWidth, int screenHeight) {
        int frameWidth = Math.clamp(screenWidth - 56, WINDOW_WIDTH, MAX_LARGE_WIDTH);
        int frameHeight = Math.clamp(screenHeight - 58, WINDOW_HEIGHT, MAX_LARGE_HEIGHT);
        int x = (screenWidth - frameWidth) / 2;
        int y = Math.max(30, (screenHeight - frameHeight - 24) / 2);
        return new Frame(x, y, frameWidth, frameHeight);
    }

    static void renderInside(GuiGraphics graphics, Frame frame) {
        graphics.enableScissor(frame.contentX(), frame.contentY(), frame.contentRight(), frame.contentBottom());
        for (int x = frame.contentX(); x < frame.contentRight(); x += 16) {
            for (int y = frame.contentY(); y < frame.contentBottom(); y += 16) {
                int tileWidth = Math.min(16, frame.contentRight() - x);
                int tileHeight = Math.min(16, frame.contentBottom() - y);
                graphics.blit(BACKGROUND, x, y, 0.0F, 0.0F, tileWidth, tileHeight, 16, 16);
            }
        }
        graphics.disableScissor();
    }

    static void renderWindow(GuiGraphics graphics, Font font, Frame frame, Component title) {
        if (frame.width() == WINDOW_WIDTH && frame.height() == WINDOW_HEIGHT) {
            graphics.blit(WINDOW, frame.x(), frame.y(), 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        } else {
            renderLargeFrame(graphics, frame);
        }
        graphics.drawString(font, title, frame.x() + 8, frame.y() + 6, 0x404040, false);
    }

    private static void renderLargeFrame(GuiGraphics graphics, Frame frame) {
        int left = frame.x();
        int top = frame.y();
        int right = frame.right();
        int bottom = frame.bottom();
        graphics.fill(left, top, right, top + 18, 0xFFC6C6C6);
        graphics.fill(left, top + 18, left + 9, bottom, 0xFFC6C6C6);
        graphics.fill(right - 9, top + 18, right, bottom, 0xFFC6C6C6);
        graphics.fill(left + 9, bottom - 9, right - 9, bottom, 0xFFC6C6C6);
        graphics.hLine(left, right - 1, top, 0xFFFFFFFF);
        graphics.vLine(left, top, bottom - 1, 0xFFFFFFFF);
        graphics.hLine(left, right - 1, bottom - 1, 0xFF202020);
        graphics.vLine(right - 1, top, bottom - 1, 0xFF202020);
        graphics.hLine(left + 5, right - 6, top + 16, 0xFF555555);
        graphics.vLine(left + 7, top + 17, bottom - 7, 0xFF555555);
        graphics.hLine(left + 7, right - 8, bottom - 7, 0xFFFFFFFF);
        graphics.vLine(right - 8, top + 17, bottom - 7, 0xFFFFFFFF);
    }

    static void renderWorkbench(
            GuiGraphics graphics,
            Font font,
            Component title,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.fill(left, top, right, bottom, 0xFFC6C6C6);
        graphics.fill(left + 4, top + 17, right - 4, bottom - 4, 0xFF101010);
        graphics.enableScissor(left + 5, top + 18, right - 5, bottom - 5);
        for (int x = left + 5; x < right - 5; x += 16) {
            for (int y = top + 18; y < bottom - 5; y += 16) {
                graphics.blit(BACKGROUND, x, y, 0.0F, 0.0F,
                        Math.min(16, right - 5 - x), Math.min(16, bottom - 5 - y), 16, 16);
            }
        }
        graphics.disableScissor();
        graphics.hLine(left, right - 1, top, 0xFFFFFFFF);
        graphics.vLine(left, top, bottom - 1, 0xFFFFFFFF);
        graphics.hLine(left, right - 1, bottom - 1, 0xFF202020);
        graphics.vLine(right - 1, top, bottom - 1, 0xFF202020);
        graphics.drawString(font, title, left + 7, top + 5, 0x404040, false);
    }

    static void renderInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0xB0101010);
        graphics.hLine(left, right - 1, top, 0xFF000000);
        graphics.vLine(left, top, bottom - 1, 0xFF000000);
        graphics.hLine(left + 1, right - 1, bottom - 1, 0xFF8A8A8A);
        graphics.vLine(right - 1, top + 1, bottom - 1, 0xFF8A8A8A);
    }

    static void renderNodeFrame(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            NodeFrame frame,
            boolean selected
    ) {
        ResourceLocation sprite = ResourceLocation.withDefaultNamespace(
                "advancements/" + frame.sprite + "_frame_" + (frame.obtained ? "obtained" : "unobtained"));
        if (selected) {
            graphics.fill(centerX - 15, centerY - 15, centerX + 15, centerY + 15, 0xA0FFFFFF);
        }
        graphics.blitSprite(sprite, centerX - 13, centerY - 13, 26, 26);
    }

    static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    enum NodeFrame {
        LOCKED("task", false),
        AVAILABLE("goal", false),
        OWNED("challenge", true);

        private final String sprite;
        private final boolean obtained;

        NodeFrame(String sprite, boolean obtained) {
            this.sprite = sprite;
            this.obtained = obtained;
        }
    }

    record Frame(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int contentX() {
            return x + 9;
        }

        int contentY() {
            return y + 18;
        }

        int contentRight() {
            return right() - 9;
        }

        int contentBottom() {
            return bottom() - 9;
        }

        int contentWidth() {
            return contentRight() - contentX();
        }

        int contentHeight() {
            return contentBottom() - contentY();
        }

        int footerY() {
            return bottom() + 4;
        }
    }
}
