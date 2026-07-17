package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.SkillProgress;
import com.envisione.progressiveskills.server.skill.SkillRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class SkillCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private SkillCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("xp").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("source")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("key", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        SkillRuntime.catalog().stream()
                                                                .flatMap(catalog -> catalog.customXpRoutes().keySet().stream())
                                                                .map(ResourceLocation::toString),
                                                        builder
                                                ))
                                                .executes(context -> custom(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "key")
                                                )))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> suggestSkills(builder))
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(context -> manual(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "skill"),
                                                        StringArgumentType.getString(context, "amount")
                                                ))))))
                .then(Commands.literal("skill")
                        .then(Commands.literal("get")
                                .then(Commands.argument("skill", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> suggestSkills(builder))
                                        .executes(context -> get(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(context, "skill")
                                        ))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSkills(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
                SkillRuntime.catalog().stream().flatMap(catalog -> catalog.skills().keySet().stream())
                        .map(ResourceLocation::toString),
                builder
        );
    }

    private static int manual(
            CommandSourceStack source,
            ServerPlayer target,
            ResourceLocation skillId,
            String amount
    ) {
        try {
            SkillCatalog catalog = SkillRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Skill catalog is unavailable")
            );
            var skill = catalog.skill(skillId).orElseThrow(
                    () -> new IllegalArgumentException("Unknown skill " + skillId)
            );
            var result = SkillRuntime.awardManual(actor(source, target), target, skill, FixedPoint.parse(amount));
            return report(source, target, result);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int custom(
            CommandSourceStack source,
            ServerPlayer target,
            ResourceLocation key
    ) {
        try {
            SkillCatalog catalog = SkillRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Skill catalog is unavailable")
            );
            var route = catalog.customXpRoute(key).orElseThrow(
                    () -> new IllegalArgumentException("Unknown custom XP key " + key)
            );
            return report(source, target, SkillRuntime.awardCustom(actor(source, target), target, route));
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int get(CommandSourceStack source, ResourceLocation skillId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            SkillCatalog catalog = SkillRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Skill catalog is unavailable")
            );
            var skill = catalog.skill(skillId).orElseThrow(
                    () -> new IllegalArgumentException("Unknown skill " + skillId)
            );
            var context = TransactionRuntime.context(source.getServer()).orElseThrow(
                    () -> new IllegalStateException("Transaction runtime is unavailable")
            );
            SkillProgress progress = SkillProgress.from(skill, context.service().snapshot(player.getUUID()));
            long next = progress.level() == skill.curve().maxLevel()
                    ? 0 : skill.curve().costUnitsAt(progress.level());
            success(source, skill.presentation().display().fallback() + ". Level " + progress.level()
                    + ". Highest " + progress.highestLevel() + ". XP "
                    + FixedPoint.format(progress.activeXpUnits()) + ". Into level "
                    + FixedPoint.format(progress.intoLevelUnits()) + " of " + FixedPoint.format(next)
                    + ". Banked " + FixedPoint.format(progress.bankedXpUnits()) + ".");
            for (var award : skill.currencyAwards()) {
                long balance = context.service().snapshot(player.getUUID()).balances()
                        .getOrDefault(award.currency(), 0L);
                success(source, award.currency() + " balance " + balance + ".");
            }
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException
                 | IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int report(
            CommandSourceStack source,
            ServerPlayer target,
            SkillRuntime.AwardResult result
    ) {
        if (!result.transaction().status().committed()) {
            failure(source, result.transaction().diagnosticCode() + ". " + result.transaction().message());
            return 0;
        }
        success(source, "Awarded " + FixedPoint.format(result.plan().awardedUnits()) + " XP to "
                + target.getGameProfile().getName() + " for " + result.skill().id() + ". Level "
                + result.plan().before().level() + " to " + result.plan().after().level() + ". Banked "
                + FixedPoint.format(result.plan().bankedAwardUnits()) + ".");
        return 1;
    }

    private static UUID actor(CommandSourceStack source, ServerPlayer target) {
        return source.getEntity() == null ? target.getUUID() : source.getEntity().getUUID();
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
