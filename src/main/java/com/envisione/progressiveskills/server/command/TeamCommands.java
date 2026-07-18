package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.social.SharedProgressionProvider;
import com.envisione.progressiveskills.server.social.SocialProviderRuntime;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class TeamCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private TeamCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("team")
                        .then(Commands.literal("create")
                                .then(Commands.argument("team", ResourceLocationArgument.id())
                                        .executes(context -> run(context.getSource(), player -> success(
                                                context.getSource(), "Team " + data(context.getSource())
                                                        .create(player.getUUID(), ResourceLocationArgument.getId(
                                                                context, "team")).scopeId() + " created.")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> run(context.getSource(), player -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            data(context.getSource()).invite(player.getUUID(), target.getUUID());
                                            success(context.getSource(), "Team invitation sent.");
                                            target.sendSystemMessage(Component.literal(PREFIX
                                                    + "Team invitation received. Run ps team accept."));
                                        }))))
                        .then(Commands.literal("accept").executes(context -> run(context.getSource(), player ->
                                success(context.getSource(), "Joined team "
                                        + data(context.getSource()).accept(player.getUUID()).scopeId() + "."))))
                        .then(Commands.literal("leave").executes(context -> run(context.getSource(), player -> {
                            data(context.getSource()).leave(player.getUUID());
                            success(context.getSource(), "Left the team.");
                        })))
                        .then(Commands.literal("owner")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> run(context.getSource(), player -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            data(context.getSource()).transferOwnership(
                                                    player.getUUID(), target.getUUID());
                                            success(context.getSource(), "Team ownership transferred.");
                                        }))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> run(context.getSource(), player -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            data(context.getSource()).remove(player.getUUID(), target.getUUID());
                                            success(context.getSource(), "Team member removed.");
                                        }))))
                        .then(Commands.literal("disband").executes(context -> run(
                                context.getSource(), player -> {
                                    data(context.getSource()).delete(player.getUUID());
                                    success(context.getSource(), "Team disbanded.");
                                })))
                        .then(Commands.literal("status").executes(context -> run(context.getSource(), player ->
                                success(context.getSource(), teamStatus(
                                        data(context.getSource()), player.getUUID()) + "."))))
                        .then(Commands.literal("contribute").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(context -> run(context.getSource(), player ->
                                                success(context.getSource(), contribute(
                                                        data(context.getSource()), player.getUUID(),
                                                        LongArgumentType.getLong(context, "amount")).toString()
                                                        + "."))))))
                .then(Commands.literal("community").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("goal", ResourceLocationArgument.id())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .then(Commands.argument("maximum", LongArgumentType.longArg(1))
                                                .executes(context -> {
                                                    long value = data(context.getSource()).contributeCommunity(
                                                            ResourceLocationArgument.getId(context, "goal"),
                                                            LongArgumentType.getLong(context, "amount"),
                                                            LongArgumentType.getLong(context, "maximum"));
                                                    success(context.getSource(), "Community progress " + value + ".");
                                                    return 1;
                                                }))))));
    }

    private static int run(CommandSourceStack source, Operation operation) {
        try {
            operation.run(source.getPlayerOrException());
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(PREFIX
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())));
            return 0;
        }
    }

    private static SharedProgressionProvider data(CommandSourceStack source) {
        return SocialProviderRuntime.shared(source.getServer());
    }

    private static String teamStatus(SharedProgressionProvider provider, java.util.UUID player) {
        return provider.scopeId(player).flatMap(provider::snapshot)
                .map(Object::toString).orElse("No team.");
    }

    private static SharedProgressionProvider.SharedSnapshot contribute(
            SharedProgressionProvider provider,
            java.util.UUID player,
            long amount
    ) {
        ResourceLocation scope = provider.scopeId(player).orElseThrow(
                () -> new IllegalStateException("Player is not in a team"));
        var snapshot = provider.snapshot(scope).orElseThrow(
                () -> new IllegalStateException("Shared progression scope is unavailable"));
        return provider.contribute(
                player, SharedProgressionProvider.DEFAULT_XP_BALANCE, amount, snapshot.revision());
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    @FunctionalInterface
    private interface Operation {
        void run(ServerPlayer player) throws Exception;
    }
}
