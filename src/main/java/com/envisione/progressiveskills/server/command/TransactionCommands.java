package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.server.transaction.Phase4LifecycleDemo;
import com.envisione.progressiveskills.server.transaction.Phase4SelfTest;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-visible Phase 4 transaction and lifecycle checkpoint commands. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class TransactionCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";
    private static final int MAX_AUDIT_LINES = 8;

    private TransactionCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("lifecycle").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("demo").requires(source -> source.hasPermission(4))
                                .executes(context -> demo(context.getSource())))
                        .then(Commands.literal("coowner").requires(source -> source.hasPermission(4))
                                .executes(context -> coowner(context.getSource())))
                        .then(Commands.literal("recompute").requires(source -> source.hasPermission(4))
                                .executes(context -> recompute(context.getSource())))
                        .then(Commands.literal("revoke").requires(source -> source.hasPermission(4))
                                .then(Commands.literal("primary")
                                        .executes(context -> revoke(context.getSource(), true)))
                                .then(Commands.literal("secondary")
                                        .executes(context -> revoke(context.getSource(), false))))
                        .then(Commands.literal("audit").requires(source -> source.hasPermission(4))
                                .executes(context -> audit(context.getSource())))
                        .then(Commands.literal("selftest").requires(source -> source.hasPermission(4))
                                .executes(context -> selfTest(context.getSource())))));
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var context = runtime(source);
        if (context == null) {
            return 0;
        }
        sendStatus(source, context.service().snapshot(player.getUUID()));
        success(source, "Lifecycle state is backed by the Phase 5 versioned player attachment.");
        return 1;
    }

    private static int demo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var context = runtime(source);
        var definition = TransactionRuntime.currentDefinition();
        if (context == null || definition.isEmpty()) {
            failure(source, "A live definition generation is required");
            return 0;
        }
        var plan = Phase4LifecycleDemo.primary(
                player.getUUID(), player.getUUID(), context.service().snapshot(player.getUUID()),
                definition.orElseThrow()
        );
        return report(source, context.executeAndPersist(player, plan, definition.orElseThrow()),
                context.service().snapshot(player.getUUID()));
    }

    private static int coowner(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var context = runtime(source);
        var definition = TransactionRuntime.currentDefinition();
        if (context == null || definition.isEmpty()) {
            failure(source, "A live definition generation is required");
            return 0;
        }
        var plan = Phase4LifecycleDemo.coowner(
                player.getUUID(), player.getUUID(), context.service().snapshot(player.getUUID()),
                definition.orElseThrow()
        );
        return report(source, context.executeAndPersist(player, plan, definition.orElseThrow()),
                context.service().snapshot(player.getUUID()));
    }

    private static int revoke(
            CommandSourceStack source,
            boolean primary
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var context = runtime(source);
        var definition = TransactionRuntime.currentDefinition();
        if (context == null || definition.isEmpty()) {
            failure(source, "A live definition generation is required");
            return 0;
        }
        var plan = Phase4LifecycleDemo.revoke(
                player.getUUID(), player.getUUID(), context.service().snapshot(player.getUUID()),
                definition.orElseThrow(), primary
        );
        return report(source, context.executeAndPersist(player, plan, definition.orElseThrow()),
                context.service().snapshot(player.getUUID()));
    }

    private static int recompute(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var context = runtime(source);
        if (context == null) {
            return 0;
        }
        var report = context.service().recompute(player.getUUID(), context.projector());
        if (!report.successful()) {
            failure(source, report.message());
            return 0;
        }
        success(source, report.message() + "; persistent changes " + report.changes().size()
                + "; transition actions executed 0");
        sendStatus(source, context.service().snapshot(player.getUUID()));
        return 1;
    }

    private static int audit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var context = runtime(source);
        if (context == null) {
            return 0;
        }
        var records = context.service().audit(player.getUUID());
        if (records.isEmpty()) {
            success(source, "No persisted lifecycle audit records exist for this player.");
            return 1;
        }
        int start = Math.max(0, records.size() - MAX_AUDIT_LINES);
        for (int index = start; index < records.size(); index++) {
            var record = records.get(index);
            success(source, record.transactionId() + " " + record.status() + " rev "
                    + record.beforeRevision() + " -> " + record.afterRevision()
                    + "; actions " + record.actionResults().size()
                    + "; reversible " + record.reversible());
        }
        return 1;
    }

    private static int selfTest(CommandSourceStack source) {
        Phase4SelfTest.Result result = Phase4SelfTest.run();
        if (!result.successful()) {
            failure(source, "Phase 4 self-test failed: " + result.detail());
            return 0;
        }
        success(source, "Phase 4 self-test passed: " + result.detail());
        return 1;
    }

    private static int report(CommandSourceStack source, TransactionResult result, ProgressionSnapshot snapshot) {
        if (!result.status().committed()) {
            failure(source, result.diagnosticCode() + ": " + result.message());
            return 0;
        }
        success(source, (result.replayed() ? "Idempotent replay" : "Committed") + " transaction "
                + result.transactionId() + "; revision " + result.beforeRevision() + " -> "
                + result.afterRevision() + "; transition results " + result.actionResults().size());
        if (result.replayed()) {
            success(source, "Cached original result returned; transition actions executed during this replay: 0");
        } else {
            result.actionResults().forEach(action -> success(source,
                    action.disposition() + ": " + action.detail()));
        }
        sendStatus(source, snapshot);
        return 1;
    }

    private static void sendStatus(CommandSourceStack source, ProgressionSnapshot snapshot) {
        long points = snapshot.balances().getOrDefault(Phase4LifecycleDemo.DEMO_POINTS, 0L);
        long health = snapshot.projectedValues().getOrDefault(Phase4LifecycleDemo.MAX_HEALTH, 0L);
        int owners = snapshot.ownership().getOrDefault(Phase4LifecycleDemo.MAX_HEALTH, java.util.Map.of()).size();
        success(source, "Lifecycle revision " + snapshot.stateRevision() + ": demo points " + points
                + ", max-health bonus " + health + " (" + owners + " owners), receipts "
                + snapshot.receiptCount() + ", audit " + snapshot.auditCount());
    }

    private static TransactionRuntime.Context runtime(CommandSourceStack source) {
        var context = TransactionRuntime.context(source.getServer());
        if (context.isEmpty()) {
            failure(source, "Phase 4 transaction runtime is unavailable");
            return null;
        }
        return context.orElseThrow();
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }
}
