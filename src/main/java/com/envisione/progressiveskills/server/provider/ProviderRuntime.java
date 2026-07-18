package com.envisione.progressiveskills.server.provider;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.provider.ExternalProviderMarker;
import com.envisione.progressiveskills.common.provider.CapabilityProfileCatalog;
import com.envisione.progressiveskills.common.provider.CompatibilityPlan;
import com.envisione.progressiveskills.common.provider.ProviderRegistry;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class ProviderRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<ProviderRegistry> REGISTRY = new AtomicReference<>();
    private static final List<ProviderAdapterLoader.LoadedAdapter> LOADED_ADAPTERS = new ArrayList<>();
    private static final Map<String, String> ADAPTER_ERRORS = new LinkedHashMap<>();

    private ProviderRuntime() {
    }

    @SubscribeEvent
    static void start(ServerAboutToStartEvent event) {
        closeAdapters();
        ProviderRegistry registry = ProviderRegistry.nativeRegistry();
        registerMarker(registry, "progressivestages", "progressivestages");
        registerMarker(registry, "irons_spellbooks", "irons_spellbooks");
        registerMarker(registry, "curios", "curios");
        registerMarker(registry, "ftbteams", "ftbteams");
        registerMarker(registry, "jei", "jei");
        registerMarker(registry, "emi", "emi");
        registerMarker(registry, "jade", "jade");
        registerMarker(registry, "patchouli", "patchouli");
        registerMarker(registry, "kubejs", "kubejs");
        loadConfiguredAdapters(event.getServer().getWorldPath(LevelResource.ROOT), registry);
        registry.probeAll();
        REGISTRY.set(registry);
    }

    @SubscribeEvent
    static void stop(ServerStoppingEvent event) {
        REGISTRY.set(null);
        closeAdapters();
    }

    private static void closeAdapters() {
        synchronized (LOADED_ADAPTERS) {
            LOADED_ADAPTERS.forEach(adapter -> {
                try {
                    adapter.close();
                } catch (IOException exception) {
                    LOGGER.warn("ProgressiveSkills provider adapter could not be closed", exception);
                }
            });
            LOADED_ADAPTERS.clear();
            ADAPTER_ERRORS.clear();
        }
    }

    public static Optional<ProviderRegistry> registry() {
        return Optional.ofNullable(REGISTRY.get());
    }

    public static CapabilityProfileCatalog profiles() {
        return PackRuntime.service().filter(service -> service.live().generation() > 0)
                .map(service -> CapabilityProfileCatalog.from(service.live().snapshot().canonicalIr()))
                .orElseGet(() -> CapabilityProfileCatalog.from(
                        com.envisione.progressiveskills.common.ir.CanonicalIr.of(
                                List.of(), com.envisione.progressiveskills.common.id.AliasMap.empty())));
    }

    public static Optional<CompatibilityPlan> activePlan() {
        ProviderRegistry registry = REGISTRY.get();
        if (registry == null) {
            return Optional.empty();
        }
        return profiles().active().map(entry -> registry.resolve(entry.profile()));
    }

    public static Map<String, String> adapterErrors() {
        synchronized (LOADED_ADAPTERS) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(ADAPTER_ERRORS));
        }
    }

    private static void registerMarker(ProviderRegistry registry, String id, String modId) {
        var mod = ModList.get().getModContainerById(modId);
        String version = mod.map(value -> value.getModInfo().getVersion().toString()).orElse("absent");
        registry.register(new ExternalProviderMarker(
                "progressiveskills." + id,
                version,
                mod.isPresent(),
                false
        ));
    }

    private static void loadConfiguredAdapters(Path worldRoot, ProviderRegistry registry) {
        Path directory = worldRoot.resolve("serverconfig/progressiveskills/adapters")
                .toAbsolutePath().normalize();
        Path artifacts = directory.resolve("artifacts").toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            return;
        }
        try {
            Path trustedWorld = worldRoot.toRealPath();
            Path trustedDirectory = directory.toRealPath();
            if (!trustedDirectory.startsWith(trustedWorld)) {
                throw new IllegalArgumentException("Provider adapter directory escapes the world root");
            }
            if (!Files.isDirectory(artifacts, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(artifacts)) {
                return;
            }
            Path trustedArtifacts = artifacts.toRealPath();
            if (!trustedArtifacts.startsWith(trustedDirectory)) {
                throw new IllegalArgumentException("Provider adapter artifact directory is unsafe");
            }
            try (var paths = Files.list(trustedDirectory)) {
            List<Path> manifests = paths.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted().limit(33).toList();
            if (manifests.size() > 32) {
                throw new IllegalArgumentException("Provider adapter manifest count exceeds capacity");
            }
            for (Path manifest : manifests) {
                    loadConfiguredAdapter(manifest, trustedArtifacts, registry);
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("ProgressiveSkills provider adapter discovery failed", exception);
            synchronized (LOADED_ADAPTERS) {
                ADAPTER_ERRORS.put("discovery", safeMessage(exception));
            }
        }
    }

    private static void loadConfiguredAdapter(Path manifest, Path artifacts, ProviderRegistry registry) {
        String name = manifest.getFileName().toString();
        try {
            if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(manifest) > 65_536) {
                throw new IllegalArgumentException("Provider adapter manifest is invalid");
            }
            var values = new Properties();
            try (InputStream input = Files.newInputStream(manifest)) {
                values.load(input);
            }
            String artifactName = values.getProperty("artifact", "");
            String sha256 = values.getProperty("sha256", "");
            if (!artifactName.matches("[a-zA-Z0-9_.]{1,128}\\.jar")
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Provider adapter pin is invalid");
            }
            Path artifact = artifacts.resolve(artifactName).normalize();
            if (!artifact.startsWith(artifacts) || Files.isSymbolicLink(artifact)
                    || !Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Provider adapter artifact path is unsafe");
            }
            Path trustedArtifact = artifact.toRealPath();
            if (!trustedArtifact.startsWith(artifacts)) {
                throw new IllegalArgumentException("Provider adapter artifact escapes its pinned directory");
            }
            ProviderAdapterLoader.LoadedAdapter loaded = ProviderAdapterLoader.load(trustedArtifact, sha256);
            try {
                registry.registerAll(loaded.providers());
            } catch (RuntimeException exception) {
                loaded.close();
                throw exception;
            }
            synchronized (LOADED_ADAPTERS) {
                LOADED_ADAPTERS.add(loaded);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("ProgressiveSkills provider adapter failed to load from {}", name, exception);
            synchronized (LOADED_ADAPTERS) {
                ADAPTER_ERRORS.put(name, safeMessage(exception));
            }
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
