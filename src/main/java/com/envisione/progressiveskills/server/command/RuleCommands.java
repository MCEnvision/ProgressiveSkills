package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.server.rule.BlockProvenanceSavedData;
import com.envisione.progressiveskills.server.rule.RuleRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        event.getDispatcher().register(Commands.literal("pskills")
                .then(Commands.literal("rule")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("preview")
                                .then(Commands.argument("rule", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                RuleRuntime.catalog().stream()
                                                        .flatMap(catalog -> catalog.rules().keySet().stream())
                                                        .map(ResourceLocation::toString),
                                                builder
                                        ))
                                        .executes(context -> preview(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(context, "rule")
                                        )))))
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
        var provenance = BlockProvenanceSavedData.get(source.getServer());
        success(source, "Block provenance tracked " + provenance.trackedCount()
                + ". Reliable " + provenance.reliable() + ".");
        provenance.issue().ifPresent(issue -> failure(source, "Block provenance issue. " + issue + "."));
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
                    + ". Origin " + value.origin().serializedName()
                    + ". Candidates " + value.candidates() + ". Eligible " + value.eligible()
                    + ". Selected " + value.selected() + ". Awarded " + value.awarded()
                    + " XP. Outcome " + value.outcome() + ".");
            success(source, "Requirements checked " + value.requirementsChecked()
                    + ". Amount before anti exploit " + value.preAntiAmount()
                    + " XP. Rounding " + (value.rounding().isEmpty() ? "none" : value.rounding()) + ".");
            if (!value.requirementFailure().isEmpty()) {
                failure(source, "Requirement failure. " + value.requirementFailure() + ".");
            }
            if (!value.transactionId().isEmpty()) {
                success(source, "Transaction " + value.transactionId() + ".");
            }
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            failure(source, "This command requires a player.");
            return 0;
        }
    }

    private static int preview(CommandSourceStack source, ResourceLocation ruleId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var preview = RuleRuntime.preview(player, ruleId);
            if (preview.isEmpty()) {
                failure(source, "Unknown rule or unavailable rule runtime. " + ruleId + ".");
                return 0;
            }
            var value = preview.orElseThrow();
            success(source, "Rule " + value.ruleId() + ". Requirements passed "
                    + value.requirementsPassed() + ". Checked " + value.requirementsChecked()
                    + ". Dependencies " + value.dependencyCount() + ". Amount " + value.amount()
                    + " XP. Rounding " + value.rounding() + ".");
            if (!value.firstFailure().isEmpty()) {
                failure(source, "Requirement failure. " + value.firstFailure() + ".");
            }
            return value.requirementsPassed() ? 1 : 0;
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
