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
            new Action("Open ability wheel", () -> open(new AbilityWheelScreen())),
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

    public CommandPaletteScreen() {
        super(Component.translatable("screen.progressiveskills.palette.title"));
    }

    @Override
    protected void init() {
        search = new EditBox(font, Math.max(8, width / 2 - 120), 30, 240, 20,
                Component.translatable("screen.progressiveskills.palette.search"));
        search.setValue(query);
        search.setResponder(value -> {
            if (!value.equals(query)) {
                query = value;
                rebuildWidgets();
            }
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        int y = 58;
        for (Action action : ACTIONS) {
            if (!normalized.isEmpty() && !action.label().toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            addRenderableWidget(Button.builder(Component.literal(action.label()), button -> action.run())
                    .bounds(Math.max(8, width / 2 - 120), y, 240, 20).build());
            y += 24;
            if (y > height - 34) {
                break;
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(Math.max(8, width / 2 - 50), height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        graphics.fill(6, 6, width - 6, height - 6, 0xE8101010);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
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

    private record Action(String label, Runnable operation) {
        private void run() {
            operation.run();
        }
    }
}
