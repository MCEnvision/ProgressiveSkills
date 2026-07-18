package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.network.ClientNetworkState;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;

public final class AbilityWheelOverlay {
    private static final ResourceLocation READY_FRAME = ResourceLocation.withDefaultNamespace(
            "advancements/task_frame_obtained");
    private static final ResourceLocation WAITING_FRAME = ResourceLocation.withDefaultNamespace(
            "advancements/task_frame_unobtained");
    private static final ResourceLocation EMPTY_FRAME = ResourceLocation.withDefaultNamespace(
            "advancements/goal_frame_unobtained");
    private static final double POINTER_LIMIT = 58.0D;
    private static final double DEAD_ZONE = 12.0D;

    private static boolean active;
    private static double previousMouseX;
    private static double previousMouseY;
    private static double pointerX;
    private static double pointerY;
    private static int pointedSlot = -1;
    private static int hoveredSlot = -1;

    private AbilityWheelOverlay() {
    }

    public static void tick(Minecraft minecraft, boolean held) {
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        if (active && (minecraft.player == null || minecraft.screen != null
                || !minecraft.isWindowActive()
                || snapshot.phase() != ClientNetworkState.ClientPhase.ACTIVE
                || snapshot.visibleState().isEmpty())) {
            cancel();
            return;
        }
        if (!held) {
            if (active) {
                finish();
            }
            return;
        }
        if (minecraft.player == null || minecraft.screen != null
                || !minecraft.isWindowActive()
                || snapshot.phase() != ClientNetworkState.ClientPhase.ACTIVE
                || snapshot.visibleState().isEmpty()) {
            cancel();
            return;
        }
        if (!active) {
            begin(minecraft, snapshot.visibleState().orElseThrow());
        }
        updatePointer(minecraft, snapshot.visibleState().orElseThrow().abilitySlots());
    }

    public static void render(GuiGraphics graphics) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        Optional<VisiblePlayerState> visible = snapshot.visibleState();
        if (minecraft.player == null || minecraft.screen != null || visible.isEmpty()) {
            cancel();
            return;
        }
        updatePointer(minecraft, visible.orElseThrow().abilitySlots());
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int radius = Math.max(50, Math.min(72, Math.min(graphics.guiWidth(), graphics.guiHeight()) / 3));
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x58000000);
        fillCircle(graphics, centerX, centerY, radius + 18, 0xB0181818);
        fillCircle(graphics, centerX, centerY, radius - 17, 0xD0080808);
        for (int slot = 0; slot < AbilityWheelLayout.SLOT_COUNT; slot++) {
            double boundary = Math.PI * 2.0D * slot / AbilityWheelLayout.SLOT_COUNT - Math.PI / 8.0D;
            int outerX = centerX + (int) Math.round(Math.cos(boundary - Math.PI / 2.0D) * (radius + 15));
            int outerY = centerY + (int) Math.round(Math.sin(boundary - Math.PI / 2.0D) * (radius + 15));
            drawLine(graphics, centerX, centerY, outerX, outerY, 0x806A6A6A);
        }
        Map<Integer, ResourceLocation> slots = visible.orElseThrow().abilitySlots();
        for (int slot = 0; slot < AbilityWheelLayout.SLOT_COUNT; slot++) {
            renderSlot(graphics, snapshot, visible.orElseThrow(), slots, slot, centerX, centerY, radius);
        }
        int cursorX = centerX + (int) Math.round(pointerX);
        int cursorY = centerY + (int) Math.round(pointerY);
        fillCircle(graphics, cursorX, cursorY, 3, hoveredSlot >= 0 ? 0xFFFFFFFF : 0xFF909090);
        Component centerLabel = hoveredLabel(snapshot, slots);
        Component centerStatus = hoveredStatus(snapshot, visible.orElseThrow(), slots);
        int labelWidth = minecraft.font.width(centerLabel);
        int statusWidth = minecraft.font.width(centerStatus);
        int centerWidth = Math.max(labelWidth, statusWidth);
        graphics.fill(centerX - centerWidth / 2 - 5, centerY - 13,
                centerX + centerWidth / 2 + 5, centerY + 13, 0xE0000000);
        graphics.drawCenteredString(minecraft.font, centerLabel, centerX, centerY - 9, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, centerStatus, centerX, centerY + 2, 0xB8D8F8);
        graphics.drawCenteredString(minecraft.font,
                Component.translatable("screen.progressiveskills.ability_wheel.release"),
                centerX, centerY + radius + 24, 0xE0E0E0);
    }

    public static boolean isActive() {
        return active;
    }

    public static void cancel() {
        active = false;
        pointedSlot = -1;
        hoveredSlot = -1;
        pointerX = 0.0D;
        pointerY = 0.0D;
    }

    private static void begin(Minecraft minecraft, VisiblePlayerState state) {
        active = true;
        previousMouseX = minecraft.mouseHandler.xpos();
        previousMouseY = minecraft.mouseHandler.ypos();
        int selected = state.selectedAbilitySlot();
        if (selected >= 0) {
            double angle = Math.PI * 2.0D * selected / AbilityWheelLayout.SLOT_COUNT - Math.PI / 2.0D;
            pointerX = Math.cos(angle) * 28.0D;
            pointerY = Math.sin(angle) * 28.0D;
            hoveredSlot = selected;
            pointedSlot = selected;
        } else {
            pointerX = 0.0D;
            pointerY = 0.0D;
            hoveredSlot = -1;
            pointedSlot = -1;
        }
    }

    private static void updatePointer(Minecraft minecraft, Map<Integer, ResourceLocation> slots) {
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        pointerX += (mouseX - previousMouseX) * 0.65D;
        pointerY += (mouseY - previousMouseY) * 0.65D;
        previousMouseX = mouseX;
        previousMouseY = mouseY;
        double length = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
        if (length > POINTER_LIMIT) {
            pointerX = pointerX / length * POINTER_LIMIT;
            pointerY = pointerY / length * POINTER_LIMIT;
        }
        pointedSlot = AbilityWheelLayout.slotForDirection(pointerX, pointerY, DEAD_ZONE);
        hoveredSlot = slots.containsKey(pointedSlot) ? pointedSlot : -1;
    }

    private static void finish() {
        int selected = hoveredSlot;
        var state = PsNetworking.clientSnapshot().visibleState();
        cancel();
        if (selected < 0 || state.isEmpty() || !state.orElseThrow().abilitySlots().containsKey(selected)
                || state.orElseThrow().selectedAbilitySlot() == selected) {
            return;
        }
        if (!SafeRetryTray.sendOrRemember(
                "Select ability slot " + (selected + 1),
                () -> PsNetworking.sendAbilitySelect(selected))) {
            PsNetworking.requestClientResync("ability wheel selection rejected");
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("screen.progressiveskills.action_changed"), true);
            }
        }
    }

    private static void renderSlot(
            GuiGraphics graphics,
            ClientNetworkState.Snapshot snapshot,
            VisiblePlayerState state,
            Map<Integer, ResourceLocation> slots,
            int slot,
            int centerX,
            int centerY,
            int radius
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        AbilityWheelLayout.Point point = AbilityWheelLayout.slotCenter(slot, centerX, centerY, radius);
        ResourceLocation abilityId = slots.get(slot);
        VisiblePlayerState.AbilityState abilityState = abilityId == null ? null : state.abilities().get(abilityId);
        ResourceLocation frame = abilityId == null ? EMPTY_FRAME
                : abilityState != null && abilityState.ready() ? READY_FRAME : WAITING_FRAME;
        if (slot == hoveredSlot) {
            fillCircle(graphics, point.x(), point.y(), 17, 0xD0FFD966);
        } else if (slot == state.selectedAbilitySlot()) {
            fillCircle(graphics, point.x(), point.y(), 16, 0xA080C8FF);
        }
        graphics.blitSprite(frame, point.x() - 13, point.y() - 13, 26, 26);
        ItemStack icon = abilityId == null ? new ItemStack(Items.GRAY_DYE) : abilityIcon(snapshot, abilityId);
        graphics.renderItem(icon, point.x() - 8, point.y() - 8);
        graphics.drawCenteredString(minecraft.font, Component.literal(Integer.toString(slot + 1)),
                point.x(), point.y() + 15, abilityId == null ? 0x888888 : 0xFFFFFF);
        if (abilityState != null) {
            String status = abilityState.cooldownRemainingTicks() > 0
                    ? Long.toString(abilityState.cooldownRemainingTicks())
                    : abilityState.charges() + "/" + abilityState.maximumCharges();
            graphics.drawCenteredString(minecraft.font, Component.literal(status),
                    point.x(), point.y() - 23, abilityState.ready() ? 0x7CFC98 : 0xFFCC66);
        }
    }

    private static Component hoveredLabel(
            ClientNetworkState.Snapshot snapshot,
            Map<Integer, ResourceLocation> slots
    ) {
        if (hoveredSlot < 0 || !slots.containsKey(hoveredSlot)) {
            return pointedSlot >= 0
                    ? Component.translatable("screen.progressiveskills.ability_wheel.empty", pointedSlot + 1)
                    : Component.translatable("screen.progressiveskills.ability_wheel.choose");
        }
        ResourceLocation id = slots.get(hoveredSlot);
        return snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.ABILITY)
                        && entry.getKey().id().equals(id))
                .map(entry -> ProjectionPresentation.display(entry.getValue(), id.getPath()))
                .findFirst().orElseGet(() -> Component.literal(id.getPath()));
    }

    private static Component hoveredStatus(
            ClientNetworkState.Snapshot snapshot,
            VisiblePlayerState state,
            Map<Integer, ResourceLocation> slots
    ) {
        if (hoveredSlot < 0 || !slots.containsKey(hoveredSlot)) {
            return pointedSlot >= 0
                    ? Component.translatable("screen.progressiveskills.ability_wheel.unavailable")
                    : Component.translatable("screen.progressiveskills.ability_wheel.dead_zone");
        }
        ResourceLocation id = slots.get(hoveredSlot);
        VisiblePlayerState.AbilityState abilityState = state.abilities().get(id);
        String kind = snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.ABILITY)
                        && entry.getKey().id().equals(id))
                .flatMap(entry -> entry.getValue().ability().stream())
                .map(DefinitionProjection.AbilityView::kind)
                .findFirst().orElse("ability");
        if (abilityState == null) {
            return Component.translatable(
                    "screen.progressiveskills.ability_wheel.state_unavailable", kind);
        }
        if (abilityState.ready()) {
            return Component.translatable("screen.progressiveskills.ability_wheel.ready", kind);
        }
        if (abilityState.cooldownRemainingTicks() > 0) {
            return Component.translatable(
                    "screen.progressiveskills.ability_wheel.cooldown",
                    kind, abilityState.cooldownRemainingTicks());
        }
        return Component.translatable("screen.progressiveskills.ability_wheel.no_charges", kind);
    }

    private static ItemStack abilityIcon(ClientNetworkState.Snapshot snapshot, ResourceLocation id) {
        return snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.ABILITY)
                        && entry.getKey().id().equals(id))
                .map(Map.Entry::getValue)
                .map(entry -> ProjectionPresentation.icon(entry, Items.BARRIER))
                .findFirst().orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    private static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int extent = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - extent, centerY + y, centerX + extent + 1, centerY + y + 1, color);
        }
    }

    private static void drawLine(
            GuiGraphics graphics,
            int startX,
            int startY,
            int endX,
            int endY,
            int color
    ) {
        int dx = Math.abs(endX - startX);
        int sx = startX < endX ? 1 : -1;
        int dy = -Math.abs(endY - startY);
        int sy = startY < endY ? 1 : -1;
        int error = dx + dy;
        int x = startX;
        int y = startY;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == endX && y == endY) {
                break;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y += sy;
            }
        }
    }
}
