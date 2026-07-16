package com.envisione.progressiveskills;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * ProgressiveSkills loader entry point.
 *
 * <p>Content and gameplay systems are intentionally absent from this Phase 1
 * bootstrap. Later phases attach their startup listeners through explicit
 * common, server, client, and compatibility boundaries.</p>
 */
@Mod(ProjectIdentity.MOD_ID)
public final class ProgressiveSkills {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ProgressiveSkills(IEventBus modEventBus) {
        modEventBus.addListener(ProgressiveSkills::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} common bootstrap ready", ProjectIdentity.DISPLAY_NAME);
    }
}
