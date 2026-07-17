package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.pack.CanonicalSemanticDigest;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.GameRules;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.audit.PersistenceMetadataSavedData;
import com.envisione.progressiveskills.server.offline.PendingOperationCoordinator;
import com.envisione.progressiveskills.server.network.NetworkRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the online transaction cache backed by the Phase 5 player attachment authority. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class TransactionRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
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
    static void onServerStarted(ServerStartedEvent event) {
        try {
            PersistenceMetadataSavedData.get(event.getServer()).observeCurrentVersion().ifPresent(change ->
                    LOGGER.warn("[ProgressiveSkills] WORLD BACKUP REQUIRED: player-data schema changed from v{} "
                                    + "to v{}. Player attachments migrate lazily on login and retain a bounded shadow; "
                                    + "this message does not claim that ProgressiveSkills created a full world backup.",
                            change.previousVersion(), change.currentVersion()));
        } catch (RuntimeException exception) {
            LOGGER.error("[ProgressiveSkills] persistence metadata is incompatible; player-data migration "
                    + "backup tracking could not start", exception);
        }
    }

    @SubscribeEvent
    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            loadAndReproject(player, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
            if (data.active()) {
                try {
                    data.prepareDeath(
                            UUID.randomUUID(),
                            Instant.now(),
                            player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                    );
                } catch (IllegalStateException exception) {
                    LOGGER.error("[ProgressiveSkills] death operation was not prepared for {}; "
                            + "no progression death policy will run", player.getUUID(), exception);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer replacement)) {
            return;
        }
        ProgressiveSkillsData data = replacement.getData(PsDataAttachments.PLAYER_DATA);
        if (data.active()) {
            data.completeDeath(Instant.now());
        }
    }

    @SubscribeEvent
    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            loadAndReproject(player, false);
        }
    }

    @SubscribeEvent
    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Context context = CONTEXT.get();
            if (context != null && context.server() == player.getServer()) {
                context.persist(player);
                context.service().unloadAccount(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        Context context = CONTEXT.getAndSet(null);
        if (context != null) {
            context.server().getPlayerList().getPlayers().forEach(player -> {
                context.persist(player);
                PlayerPersistentProjector.clearKnownModifier(player);
            });
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

    /** Reconciles all online attachments and starts a fresh digest bound session after publish. */
    public static void onDefinitionsPublished(MinecraftServer server) {
        Context context = CONTEXT.get();
        if (context == null || context.server() != server) {
            return;
        }
        server.getPlayerList().getPlayers().forEach(player -> {
            try {
                loadAndReproject(player, false);
            } catch (RuntimeException exception) {
                NetworkRuntime.invalidate(player);
                LOGGER.error("[ProgressiveSkills] definition publish could not reconcile player {}; "
                        + "their network session was invalidated", player.getUUID(), exception);
            }
        });
    }

    private static void loadAndReproject(ServerPlayer player, boolean applyPendingOperations) {
        Context context = CONTEXT.get();
        if (context == null || context.server() != player.getServer()) {
            return;
        }
        PlayerPersistentProjector.clearKnownModifier(player);
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        data.onLogin();
        if (!data.active()) {
            context.service().unloadAccount(player.getUUID());
            LOGGER.error("[ProgressiveSkills] quarantined player data for {}; gameplay projection is disabled",
                    player.getUUID());
            NetworkRuntime.begin(player);
            return;
        }
        reconcileDefinitions(data);
        try {
            context.service().restoreAccount(player.getUUID(), data.transactionState());
        } catch (RuntimeException exception) {
            context.service().unloadAccount(player.getUUID());
            LOGGER.error("[ProgressiveSkills] refused persisted transaction state for {}",
                    player.getUUID(), exception);
            return;
        }
        if (applyPendingOperations) {
            PendingOperationCoordinator.applyOnLogin(player, context, UUID.randomUUID());
        }
        context.service().forceReproject(player.getUUID(), context.projector());
        currentDefinition().ifPresent(definition -> context.persist(player, definition));
        NetworkRuntime.begin(player);
    }

    private static void reconcileDefinitions(ProgressiveSkillsData data) {
        PackRuntime.service().ifPresent(service -> {
            var canonical = service.live().snapshot().canonicalIr();
            var lineages = new java.util.TreeMap<com.envisione.progressiveskills.common.id.DefinitionKey, String>();
            canonical.definitions().forEach((key, definition) ->
                    lineages.put(key, CanonicalSemanticDigest.definition(definition)));
            var report = data.reconcileDefinitions(
                    canonical.definitions().keySet(),
                    Map.copyOf(lineages),
                    canonical.aliases()
            );
            if (report.renamed() > 0 || report.orphaned() > 0 || report.restored() > 0) {
                LOGGER.info("[ProgressiveSkills] reconciled player state: {} renamed, {} orphaned, {} restored",
                        report.renamed(), report.orphaned(), report.restored());
            }
        });
    }

    public record Context(
            MinecraftServer server,
            ProgressionTransactionService service,
            PlayerPersistentProjector projector,
            MinecraftTransitionActionExecutor actionExecutor
    ) {
        public TransactionResult executeAndPersist(
                ServerPlayer player,
                CascadePlan plan,
                DefinitionRevision definition
        ) {
            TransactionResult result = service.execute(plan, definition, projector, actionExecutor);
            persist(player, definition);
            NetworkRuntime.sync(player);
            return result;
        }

        public void persist(ServerPlayer player) {
            currentDefinition().ifPresent(definition -> persist(player, definition));
        }

        public void persist(ServerPlayer player, DefinitionRevision definition) {
            if (player.getServer() != server) {
                throw new IllegalArgumentException("Player is not owned by this transaction runtime");
            }
            ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
            if (data.active()) {
                data.replaceTransactionState(service.exportAccount(player.getUUID()), definition);
            }
        }
    }
}
