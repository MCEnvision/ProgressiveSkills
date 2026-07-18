package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ClientPreferences;
import com.envisione.progressiveskills.client.SafeRetryTray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class CommandPaletteScreen extends ProgressiveScreen {
    private static final List<Action> ACTIONS = List.of(
            new Action("Open progression", () -> open(new ProgressionScreen())),
            new Action("Open skill trees", () -> open(new TreeScreen())),
            new Action("Run doctor", () -> command("pskills doctor")),
            new Action("Explain latest decision", () -> command("pskills why latest")),
            new Action("Open test center", () -> open(new ProgressionScreen(ProgressionScreen.Tab.TESTS))),
            new Action("Open sync doctor", () -> open(new ProgressionScreen(ProgressionScreen.Tab.SYNC))),
            new Action("Open authoring Studio", () -> open(new StudioScreen())),
            new Action("Edit progression HUD", () -> open(new HudEditorScreen())),
            new Action("Retry safe action", SafeRetryTray::retry),
            new Action("Toggle progression HUD", ClientPreferences::toggleHud),
            new Action("Toggle high contrast", ClientPreferences::toggleHighContrast),
            new Action("Toggle reduced motion", ClientPreferences::toggleReducedMotion),
            new Action("Toggle compact layout", ClientPreferences::toggleCompactLayout),
            new Action("Cycle text size", ClientPreferences::cycleTextScale)
    );

    private EditBox search;
    private String query = "";
    private int page;

    public CommandPaletteScreen() {
        super(Component.translatable("screen.progressiveskills.palette.title"));
    }

    @Override
    protected void init() {
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        search = new EditBox(font, frame.contentX() + 4, frame.contentY() + 4, 226, 18,
                Component.translatable("screen.progressiveskills.palette.search"));
        search.setValue(query);
        search.setResponder(value -> {
            if (!value.equals(query)) {
                query = value;
                page = 0;
                rebuildWidgets();
            }
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        List<Action> filtered = ACTIONS.stream().filter(action -> normalized.isEmpty()
                || action.label().toLowerCase(Locale.ROOT).contains(normalized)).toList();
        int pageSize = 4;
        int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        page = Math.clamp(page, 0, pages - 1);
        int y = frame.contentY() + 26;
        for (Action action : filtered.stream().skip((long) page * pageSize).limit(pageSize).toList()) {
            addRenderableWidget(Button.builder(Component.literal(action.label()), button -> action.run())
                    .bounds(frame.contentX() + 4, y, 226, 19).build());
            y += 21;
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(frame.x(), frame.footerY(), 24, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(frame.x() + 26, frame.footerY(), 24, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(frame.x() + 152, frame.footerY(), 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        AdvancementUi.renderInside(graphics, frame);
        AdvancementUi.renderWindow(graphics, font, frame, title);
        super.render(graphics, mouseX, mouseY, partialTick);
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        int count = (int) ACTIONS.stream().filter(action -> normalized.isEmpty()
                || action.label().toLowerCase(Locale.ROOT).contains(normalized)).count();
        int pages = Math.max(1, (count + 3) / 4);
        graphics.drawCenteredString(font, Component.literal((page + 1) + " of " + pages),
                frame.x() + 101, frame.footerY() + 6, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    private static void command(String value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(value);
            minecraft.setScreen(null);
        }
    }

    private void changePage(int direction) {
        page += direction;
        rebuildWidgets();
    }

    private record Action(String label, Runnable operation) {
        private void run() {
            operation.run();
        }
    }
}
