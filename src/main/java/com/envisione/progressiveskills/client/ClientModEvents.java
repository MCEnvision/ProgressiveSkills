package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/** Physical-client-only startup listeners. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean TITLE_SCREEN_REPORTED = new AtomicBoolean();

    private ClientModEvents() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        PsNetworking.configureClientConnectionIdentity(ClientConnectionIdentity::current);
        PsNetworking.configureClientIntentLifecycle(
                SafeRetryTray::onIntentSent, SafeRetryTray::onIntentResult);
        LOGGER.info("{} client bootstrap ready", ProjectIdentity.DISPLAY_NAME);
    }

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen && TITLE_SCREEN_REPORTED.compareAndSet(false, true)) {
            LOGGER.info("{} title screen ready", ProjectIdentity.DISPLAY_NAME);
        }
    }

    @SubscribeEvent
    static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PsNetworking.clientDisconnect();
        SafeRetryTray.clear();
    }
}
