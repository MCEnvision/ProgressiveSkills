package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.network.NetworkLimits;
import com.envisione.progressiveskills.server.network.NetworkRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Reports the negotiated Phase 6 session and starts safe resynchronization. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class NetworkCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private NetworkCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("network").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("resync").requires(source -> source.hasPermission(4))
                                .executes(context -> resync(context.getSource())))));
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var status = NetworkRuntime.status(player);
        if (status.isEmpty()) {
            failure(source, "No ProgressiveSkills network session exists for this player");
            return 0;
        }
        var value = status.orElseThrow();
        success(source, "Protocol " + NetworkLimits.PROTOCOL_VERSION + " session " + value.phase()
                + "; definition generation " + value.definitionGeneration()
                + "; semantic " + shortDigest(value.semanticDigest())
                + "; presentation " + shortDigest(value.presentationDigest()));
        success(source, "Definition cache hit " + value.definitionCacheHit()
                + "; sent storage/state revisions " + value.sentSyncRevision() + "/" + value.stateRevision()
                + "; acknowledged storage revision " + value.acknowledgedSyncRevision());
        success(source, "Deltas " + value.deltaCount() + "; full resyncs " + value.resyncCount()
                + "; cached intent results " + value.cachedIntentResults());
        return value.phase() == com.envisione.progressiveskills.common.network.ServerNetworkSessions.ServerPhase.ACTIVE
                ? 1 : 0;
    }

    private static int resync(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NetworkRuntime.begin(player);
        success(source, "Started a fresh digest-bound handshake; run /ps network status after the ACK completes");
        return 1;
    }

    private static String shortDigest(String digest) {
        return digest.substring(0, Math.min(12, digest.length()));
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }
}
