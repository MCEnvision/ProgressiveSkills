package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.PartyProvider;
import com.envisione.progressiveskills.common.social.SharedProgressionProvider;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class SocialProviderRuntime {
    private static final AtomicReference<Function<MinecraftServer, PartyProvider>> PARTIES =
            new AtomicReference<>(NativeSocialProviders::parties);
    private static final AtomicReference<Function<MinecraftServer, SharedProgressionProvider>> SHARED =
            new AtomicReference<>(NativeSocialProviders::shared);

    private SocialProviderRuntime() {
    }

    public static PartyProvider parties(MinecraftServer server) {
        PartyProvider provider = PARTIES.get().apply(Objects.requireNonNull(server, "server"));
        if (!provider.available()) {
            throw new IllegalStateException("Party provider is unavailable " + provider.providerId());
        }
        return provider;
    }

    public static SharedProgressionProvider shared(MinecraftServer server) {
        SharedProgressionProvider provider = SHARED.get().apply(Objects.requireNonNull(server, "server"));
        if (!provider.available()) {
            throw new IllegalStateException("Shared progression provider is unavailable " + provider.providerId());
        }
        return provider;
    }

    public static void installPartyProvider(Function<MinecraftServer, PartyProvider> factory) {
        PARTIES.set(Objects.requireNonNull(factory, "factory"));
    }

    public static void installSharedProvider(Function<MinecraftServer, SharedProgressionProvider> factory) {
        SHARED.set(Objects.requireNonNull(factory, "factory"));
    }

    public static void restoreNativeProviders() {
        PARTIES.set(NativeSocialProviders::parties);
        SHARED.set(NativeSocialProviders::shared);
    }
}
