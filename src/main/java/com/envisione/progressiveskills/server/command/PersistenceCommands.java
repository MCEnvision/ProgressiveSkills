package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsData;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.server.audit.PlayerDataSnapshotService;
import com.envisione.progressiveskills.server.offline.PendingOperationSavedData;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;

/** Operator-visible Phase 5 attachment, snapshot, and offline-queue diagnostics. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class PersistenceCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";
    private static final PlayerDataSnapshotService SNAPSHOTS = PlayerDataSnapshotService.systemClock();

    private PersistenceCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("persistence").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("snapshot").requires(source -> source.hasPermission(3))
                                .executes(context -> snapshot(context.getSource(), false)))
                        .then(Commands.literal("export").requires(source -> source.hasPermission(3))
                                .executes(context -> snapshot(context.getSource(), true)))));
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        var view = data.view();
        var transaction = view.transactionState();
        int pending = PendingOperationSavedData.get(source.getServer()).pendingFor(player.getUUID()).size();
        success(source, "Player data v" + ProgressiveSkillsData.CURRENT_DATA_VERSION
                + " is " + view.status() + "; storage revision " + view.storageRevision()
                + "; transaction revision " + transaction.stateRevision());
        success(source, "Balances " + transaction.balances().size()
                + ", persistent owners " + transaction.ownership().size()
                + ", receipts " + transaction.receipts().size()
                + ", replay results " + transaction.idempotencyResults().size()
                + ", audit " + transaction.auditRecords().size());
        success(source, "Definition state " + view.definitionStates().size()
                + ", orphans " + view.orphans().size()
                + ", operation receipts " + view.operationReceipts().size()
                + ", pending offline operations " + pending
                + ", migration shadow " + view.migrationShadow().isPresent());
        view.quarantine().ifPresent(quarantine ->
                failure(source, "Quarantined: " + quarantine.reason() + "; raw digest " + quarantine.rawDigest()));
        return 1;
    }

    private static int snapshot(
            CommandSourceStack source,
            boolean readable
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TransactionRuntime.context(source.getServer()).ifPresent(context -> context.persist(player));
        ProgressiveSkillsData data = player.getData(PsDataAttachments.PLAYER_DATA);
        try {
            var result = readable
                    ? SNAPSHOTS.exportReadable(source.getServer(), data)
                    : SNAPSHOTS.snapshot(source.getServer(), data);
            success(source, (readable ? "Readable export" : "Verified snapshot")
                    + " wrote " + result.bytes() + " bytes; digest " + result.playerDataDigest()
                    + "; path " + result.path());
            return 1;
        } catch (IOException | RuntimeException exception) {
            failure(source, "Persistence export failed: " + safeMessage(exception));
            return 0;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 512));
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }
}
