package com.envisione.progressiveskills.server.pack;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticSeverity;
import com.envisione.progressiveskills.common.pack.AvailableEnvironment;
import com.envisione.progressiveskills.common.pack.DefinitionRegistryService;
import com.envisione.progressiveskills.common.pack.LastKnownGoodStore;
import com.envisione.progressiveskills.common.pack.PackRoot;
import com.envisione.progressiveskills.common.pack.PackRootTier;
import com.envisione.progressiveskills.common.pack.SemanticVersion;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Server lifecycle owner for staged/live content-pack registries. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class PackRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<DefinitionRegistryService> SERVICE = new AtomicReference<>();
    private static final AtomicReference<DefinitionRegistryService> PENDING_SERVICE = new AtomicReference<>();

    private PackRuntime() {}

    @SubscribeEvent
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        SERVICE.set(null);
        PENDING_SERVICE.set(null);
        Path globalRoot = FMLPaths.CONFIGDIR.get().resolve("progressiveskills/packs");
        Path worldRoot = event.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig/progressiveskills/packs");
        Path storage = event.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data/progressiveskills/definitions");
        try {
            StarterPackInstaller.install(globalRoot);
            var service = new DefinitionRegistryService(
                    java.util.List.of(
                            new PackRoot(PackRootTier.GLOBAL_CONFIG, "global", globalRoot),
                            new PackRoot(PackRootTier.WORLD_OVERLAY, "world", worldRoot)
                    ),
                    environment(),
                    new LastKnownGoodStore(storage),
                    storage.resolve("recovery")
            );
            var result = service.start();
            if (result.live().isPresent()) {
                var live = result.live().orElseThrow();
                PENDING_SERVICE.set(service);
                LOGGER.info(
                        "[ProgressiveSkills] compiled generation {}: {} packs, {} definitions, digest {}{}",
                        live.generation(),
                        live.snapshot().manifests().size(),
                        live.snapshot().canonicalIr().definitions().size(),
                        live.snapshot().contentDigest(),
                        result.recovered() ? " (last-known-good recovery)" : ""
                );
            } else {
                LOGGER.error("[ProgressiveSkills] no valid definition snapshot is available; progression routes remain disabled");
            }
            result.primaryResult().diagnostics().diagnostics().forEach(diagnostic -> {
                if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
                    LOGGER.error("[ProgressiveSkills] {}: {}", diagnostic.descriptor().code(), diagnostic.message());
                } else {
                    LOGGER.warn("[ProgressiveSkills] {}: {}", diagnostic.descriptor().code(), diagnostic.message());
                }
            });
        } catch (IOException | IllegalArgumentException | ArithmeticException exception) {
            SERVICE.set(null);
            PENDING_SERVICE.set(null);
            LOGGER.error("[ProgressiveSkills] content-pack startup failed; progression routes remain disabled", exception);
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        DefinitionRegistryService service = PENDING_SERVICE.getAndSet(null);
        if (service == null) {
            return;
        }
        try {
            var reservation = CarrierPublicationGuard.reserve(event.getServer(), service.live().snapshot());
            SERVICE.set(service);
            LOGGER.info(
                    "[ProgressiveSkills] activated generation {} after carrier archive reservation. Added {} entries and {} bytes",
                    service.live().generation(), reservation.addedEntries(), reservation.addedBytes()
            );
            com.envisione.progressiveskills.server.rule.RuleRuntime.reload(event.getServer());
        } catch (RuntimeException exception) {
            SERVICE.set(null);
            LOGGER.error("[ProgressiveSkills] carrier archive reservation failed. Progression routes remain disabled", exception);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        SERVICE.set(null);
        PENDING_SERVICE.set(null);
    }

    public static Optional<DefinitionRegistryService> service() {
        return Optional.ofNullable(SERVICE.get());
    }

    private static AvailableEnvironment environment() {
        Map<String, String> mods = new LinkedHashMap<>();
        ModList.get().getMods().forEach(mod -> mods.put(mod.getModId(), mod.getVersion().toString()));
        return new AvailableEnvironment(SemanticVersion.ENGINE_CURRENT, mods);
    }
}
