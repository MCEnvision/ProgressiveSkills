package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.server.creator.AdvancedCreatorRuntime;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class AdvancedCreatorCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private AdvancedCreatorCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("rank")
                        .then(Commands.literal("tree")
                                .then(Commands.argument("rank", ResourceLocationArgument.id())
                                        .executes(context -> rank(context.getSource(), true,
                                                ResourceLocationArgument.getId(context, "rank")))))
                        .then(Commands.literal("class")
                                .then(Commands.argument("rank", ResourceLocationArgument.id())
                                        .executes(context -> rank(context.getSource(), false,
                                                ResourceLocationArgument.getId(context, "rank"))))))
                .then(Commands.literal("stance")
                        .then(Commands.argument("stance", ResourceLocationArgument.id())
                                .executes(context -> stance(context.getSource(),
                                        ResourceLocationArgument.getId(context, "stance")))))
                .then(Commands.literal("proc").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("proc", ResourceLocationArgument.id())
                                .then(Commands.argument("trigger", ResourceLocationArgument.id())
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(context -> proc(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "proc"),
                                                        ResourceLocationArgument.getId(context, "trigger"),
                                                        LongArgumentType.getLong(context, "seed")))))))
                .then(Commands.literal("predicate")
                        .then(Commands.argument("predicate", ResourceLocationArgument.id())
                                .executes(context -> predicate(context.getSource(),
                                        ResourceLocationArgument.getId(context, "predicate")))))
                .then(Commands.literal("challenge")
                        .then(Commands.literal("progress").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("challenge", ResourceLocationArgument.id())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(context -> challenge(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "challenge"),
                                                        LongArgumentType.getLong(context, "amount")))))))
                .then(Commands.literal("context")
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .executes(context -> contextEffect(context.getSource(),
                                        ResourceLocationArgument.getId(context, "effect"))))));
    }

    private static int rank(CommandSourceStack source, boolean tree, ResourceLocation id) {
        return run(source, player -> {
            var result = AdvancedCreatorRuntime.purchaseRank(player,
                    tree ? DefinitionKinds.TREE_RANK : DefinitionKinds.CLASS_RANK, id, UUID.randomUUID());
            if (!result.status().committed()) {
                throw new IllegalStateException(result.message());
            }
            success(source, "Rank purchased.");
        });
    }

    private static int stance(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> {
            var result = AdvancedCreatorRuntime.selectStance(player, id, UUID.randomUUID());
            if (!result.status().committed()) {
                throw new IllegalStateException(result.message());
            }
            success(source, "Stance selected.");
        });
    }

    private static int proc(CommandSourceStack source, ResourceLocation proc, ResourceLocation trigger, long seed) {
        return run(source, player -> success(source,
                AdvancedCreatorRuntime.triggerProc(player, proc, trigger, seed).toString() + "."));
    }

    private static int predicate(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> {
            var result = AdvancedCreatorRuntime.predicate(player, id);
            success(source, "Predicate " + result.passed() + ". Trace " + result.trace() + ".");
        });
    }

    private static int challenge(CommandSourceStack source, ResourceLocation id, long amount) {
        return run(source, player -> success(source, "Challenge progress "
                + AdvancedCreatorRuntime.progressChallenge(player, id, amount) + "."));
    }

    private static int contextEffect(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> success(source, "Context effect applied "
                + AdvancedCreatorRuntime.applyContextEffect(player, id) + "."));
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

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    @FunctionalInterface
    private interface Operation {
        void run(ServerPlayer player) throws Exception;
    }
}
