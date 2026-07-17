package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.client.screen.TreeScreen;
import com.envisione.progressiveskills.common.network.PsNetworking;
import net.minecraft.client.Minecraft;
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
    }
}
