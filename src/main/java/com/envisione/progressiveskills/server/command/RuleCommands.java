package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.server.rule.RuleRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class RuleCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private RuleCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("rule")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource()))))
                .then(Commands.literal("explain")
                        .then(Commands.literal("xp")
                                .then(Commands.literal("last")
                                        .executes(context -> explainLast(context.getSource()))))));
    }

    private static int status(CommandSourceStack source) {
        var catalog = RuleRuntime.catalog();
        if (catalog.isEmpty()) {
            failure(source, "Rule runtime is unavailable.");
            return 0;
        }
        long enabled = catalog.orElseThrow().rules().values().stream().filter(rule -> rule.enabled()).count();
        success(source, "Rules " + catalog.orElseThrow().rules().size() + ". Enabled " + enabled + ".");
        return 1;
    }

    private static int explainLast(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var trace = RuleRuntime.lastTrace(player.getUUID());
            if (trace.isEmpty()) {
                failure(source, "No XP rule event has been observed for this session.");
                return 0;
            }
            var value = trace.orElseThrow();
            success(source, "Trigger " + value.trigger() + ". Subject " + value.subject()
                    + ". Candidates " + value.candidates() + ". Eligible " + value.eligible()
                    + ". Selected " + value.selected() + ". Awarded " + value.awarded()
                    + " XP. Outcome " + value.outcome() + ".");
            if (!value.transactionId().isEmpty()) {
                success(source, "Transaction " + value.transactionId() + ".");
            }
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            failure(source, "This command requires a player.");
            return 0;
        }
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }
}
