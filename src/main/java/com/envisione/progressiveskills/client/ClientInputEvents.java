package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.client.screen.CommandPaletteScreen;
import com.envisione.progressiveskills.client.screen.ProgressionScreen;
import com.envisione.progressiveskills.common.network.PsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID, value = Dist.CLIENT)
public final class ClientInputEvents {
    private ClientInputEvents() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        AbilityWheelOverlay.tick(minecraft, ClientKeyMappings.ABILITY_WHEEL.isDown());
        while (ClientKeyMappings.OPEN_PROGRESS.consumeClick()) {
            var snapshot = PsNetworking.clientSnapshot();
            if (minecraft.player != null && minecraft.screen == null && ProgressionScreen.isAvailable(snapshot)) {
                minecraft.setScreen(new ProgressionScreen());
            }
        }
        if (minecraft.screen != null || AbilityWheelOverlay.isActive()) {
            return;
        }
        while (ClientKeyMappings.COMMAND_PALETTE.consumeClick()) {
            minecraft.setScreen(new CommandPaletteScreen());
        }
        while (ClientKeyMappings.PREVIOUS_ABILITY.consumeClick()) {
            selectRelativeAbility(-1);
        }
        while (ClientKeyMappings.NEXT_ABILITY.consumeClick()) {
            selectRelativeAbility(1);
        }
        while (ClientKeyMappings.USE_SELECTED_ABILITY.consumeClick()) {
            useSelectedAbility();
        }
        for (int slot = 0; slot < ClientKeyMappings.DIRECT_ABILITY_SLOTS.size(); slot++) {
            while (ClientKeyMappings.DIRECT_ABILITY_SLOTS.get(slot).consumeClick()) {
                activateAbilitySlot(slot);
            }
        }
    }

    private static void selectRelativeAbility(int direction) {
        var state = PsNetworking.clientSnapshot().visibleState();
        if (state.isEmpty() || state.orElseThrow().abilitySlots().isEmpty()) {
            report("No assigned ability slot is available.");
            return;
        }
        var slots = state.orElseThrow().abilitySlots().keySet().stream().sorted().toList();
        int current = slots.indexOf(state.orElseThrow().selectedAbilitySlot());
        int next = current < 0
                ? direction > 0 ? 0 : slots.size() - 1
                : Math.floorMod(current + direction, slots.size());
        int selected = slots.get(next);
        if (!SafeRetryTray.sendOrRemember(
                "Select ability slot " + (selected + 1),
                () -> PsNetworking.sendAbilitySelect(selected))) {
            report("Ability selection is unavailable while synchronization is incomplete.");
        }
    }

    private static void useSelectedAbility() {
        var state = PsNetworking.clientSnapshot().visibleState();
        if (state.isEmpty() || state.orElseThrow().selectedAbilitySlot() < 0) {
            report("No ability slot is selected.");
            return;
        }
        activateAbilitySlot(state.orElseThrow().selectedAbilitySlot());
    }

    private static void activateAbilitySlot(int slot) {
        var snapshot = PsNetworking.clientSnapshot();
        var state = snapshot.visibleState();
        if (state.isEmpty() || !state.orElseThrow().abilitySlots().containsKey(slot)) {
            report("Ability slot " + (slot + 1) + " is empty.");
            return;
        }
        var abilityId = state.orElseThrow().abilitySlots().get(slot);
        String kind = snapshot.activeDefinitions().stream()
                .flatMap(definitions -> definitions.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().id().equals(abilityId))
                .flatMap(entry -> entry.getValue().ability().stream())
                .map(com.envisione.progressiveskills.common.network.DefinitionProjection.AbilityView::kind)
                .findFirst().orElse("");
        boolean sent = SafeRetryTray.sendOrRemember(
                "Use ability " + abilityId,
                kind.equals("toggle")
                        ? () -> PsNetworking.sendAbilityToggle(abilityId)
                        : () -> PsNetworking.sendAbilityActivate(slot));
        if (!sent) {
            report("Ability activation is unavailable while synchronization is incomplete.");
        }
    }

    private static void report(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }
}
