package com.envisione.progressiveskills.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class StudioCurveScreen extends ProgressiveScreen {
    private final String draft;
    private final String revision;
    private final String definition;
    private final String display;
    private EditBox base;
    private EditBox step;
    private EditBox levels;
    private EditBox income;
    private Preview preview = Preview.empty();
    private String status = "Enter curve and economy values.";

    public StudioCurveScreen(String draft, String revision, String definition, String display) {
        super(Component.literal("Studio Curve And Economy Preview"));
        this.draft = draft;
        this.revision = revision;
        this.definition = definition;
        this.display = display;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.max(54, Math.min(110, (width - 84) / 4));
        base = field(10, 32, fieldWidth, "Base cost", "100");
        step = field(base.getX() + fieldWidth + 4, 32, fieldWidth, "Cost step", "25");
        levels = field(step.getX() + fieldWidth + 4, 32, fieldWidth, "Levels", "10");
        income = field(levels.getX() + fieldWidth + 4, 32, fieldWidth, "XP per event", "25");
        addRenderableWidget(Button.builder(Component.literal("Calculate"), ignored -> calculate())
                .bounds(10, 56, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width - 100, height - 28, 90, 20).build());
        calculate();
        setInitialFocus(base);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        AdvancementUi.renderWorkbench(graphics, font, title, 6, 6, width - 6, height - 6);
        AdvancementUi.renderInset(graphics, 10, 84, width - 10, height - 44);
        graphics.drawString(font, Component.literal(status), 96, 62, 0xFFCC66, false);
        drawPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new StudioScreen(draft, revision, definition, display));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(title.getString() + ". " + status + ". Total XP "
                + preview.totalXp() + ". Events " + preview.totalEvents() + ".");
    }

    private EditBox field(int x, int y, int width, String hint, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(12);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void calculate() {
        try {
            long baseCost = positive(base.getValue(), "Base cost");
            long costStep = nonnegative(step.getValue(), "Cost step");
            int levelCount = Math.toIntExact(positive(levels.getValue(), "Levels"));
            long eventIncome = positive(income.getValue(), "XP per event");
            if (levelCount > 100) {
                throw new IllegalArgumentException("Preview levels must not exceed one hundred");
            }
            var costs = new ArrayList<Long>();
            long total = 0L;
            for (int level = 1; level <= levelCount; level++) {
                long cost = Math.addExact(baseCost, Math.multiplyExact(costStep, level - 1L));
                costs.add(cost);
                total = Math.addExact(total, cost);
            }
            preview = new Preview(List.copyOf(costs), total,
                    Math.floorDiv(Math.addExact(total, eventIncome - 1L), eventIncome));
            status = "Preview ready for " + levelCount + " levels.";
        } catch (RuntimeException exception) {
            preview = Preview.empty();
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private void drawPreview(GuiGraphics graphics) {
        int left = 12;
        int top = 90;
        int right = width - 12;
        int bottom = height - 48;
        graphics.drawString(font, Component.literal("Total XP " + preview.totalXp()
                + ". Events at configured income " + preview.totalEvents() + "."),
                left, top, 0x88C8FF, false);
        if (preview.costs().isEmpty() || bottom <= top + 28) {
            return;
        }
        top += 18;
        long maximum = preview.costs().stream().mapToLong(Long::longValue).max().orElse(1L);
        int graphHeight = Math.max(20, bottom - top);
        int graphWidth = Math.max(20, right - left);
        int previousX = left;
        int previousY = bottom;
        for (int index = 0; index < preview.costs().size(); index++) {
            int x = preview.costs().size() == 1 ? left + graphWidth / 2
                    : left + index * graphWidth / (preview.costs().size() - 1);
            int heightValue = java.math.BigInteger.valueOf(preview.costs().get(index))
                    .multiply(java.math.BigInteger.valueOf(graphHeight))
                    .divide(java.math.BigInteger.valueOf(maximum)).intValueExact();
            int y = bottom - heightValue;
            graphics.fill(Math.min(previousX, x), previousY, Math.max(previousX, x) + 1,
                    previousY + 2, 0xFF80D090);
            graphics.fill(x, Math.min(previousY, y), x + 2, Math.max(previousY, y) + 1, 0xFF80D090);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
            previousX = x;
            previousY = y;
        }
    }

    private static long positive(String value, String name) {
        long parsed = Long.parseLong(value.strip());
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static long nonnegative(String value, String name) {
        long parsed = Long.parseLong(value.strip());
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return parsed;
    }

    private record Preview(List<Long> costs, long totalXp, long totalEvents) {
        private static Preview empty() {
            return new Preview(List.of(), 0L, 0L);
        }
    }
}
