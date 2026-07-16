package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the bounded, explicitly session-only Phase 4 transaction runtime. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class TransactionRuntime {
    private static final AtomicReference<Context> CONTEXT = new AtomicReference<>();

    private TransactionRuntime() {
    }

    @SubscribeEvent
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        CONTEXT.set(new Context(
                server,
                ProgressionTransactionService.boundedDefaults(),
                new PlayerPersistentProjector(server),
                new MinecraftTransitionActionExecutor(server)
        ));
    }

    @SubscribeEvent
    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reproject(player);
        }
    }

    @SubscribeEvent
    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reproject(player);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        Context context = CONTEXT.getAndSet(null);
        if (context != null) {
            context.server().getPlayerList().getPlayers().forEach(PlayerPersistentProjector::clearKnownModifier);
        }
    }

    public static Optional<Context> context(MinecraftServer server) {
        Context context = CONTEXT.get();
        return context != null && context.server() == server ? Optional.of(context) : Optional.empty();
    }

    public static Optional<DefinitionRevision> currentDefinition() {
        return PackRuntime.service().map(service -> new DefinitionRevision(
                service.live().generation(),
                service.live().snapshot().contentDigest()
        ));
    }

    private static void reproject(ServerPlayer player) {
        Context context = CONTEXT.get();
        if (context == null || context.server() != player.getServer()) {
            return;
        }
        PlayerPersistentProjector.clearKnownModifier(player);
        context.service().forceReproject(player.getUUID(), context.projector());
    }

    public record Context(
            MinecraftServer server,
            ProgressionTransactionService service,
            PlayerPersistentProjector projector,
            MinecraftTransitionActionExecutor actionExecutor
    ) {
    }
}
