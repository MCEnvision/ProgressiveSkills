package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.client.screen.TreeScreen;
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
        while (ClientKeyMappings.OPEN_TREE.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            var snapshot = PsNetworking.clientSnapshot();
            if (minecraft.player != null && minecraft.screen == null && TreeScreen.isAvailable(snapshot)) {
                minecraft.setScreen(new TreeScreen());
            }
        }
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        while (ClientKeyMappings.ABILITY_WHEEL.consumeClick()) {
            selectRelativeAbility(1);
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
        if (!PsNetworking.sendAbilitySelect(selected)) {
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
        boolean sent = kind.equals("toggle")
                ? PsNetworking.sendAbilityToggle(abilityId)
                : PsNetworking.sendAbilityActivate(slot);
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
