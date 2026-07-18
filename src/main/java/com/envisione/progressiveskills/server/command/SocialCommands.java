package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.creator.BuildShareCode;
import com.envisione.progressiveskills.common.social.PartyReadiness;
import com.envisione.progressiveskills.common.social.PrivacySettings;
import com.envisione.progressiveskills.common.social.PartyProvider;
import com.envisione.progressiveskills.server.creator.CreatorRuntime;
import com.envisione.progressiveskills.server.skill.SkillRuntime;
import com.envisione.progressiveskills.server.social.MultiplayerSavedData;
import com.envisione.progressiveskills.server.social.MentorCatchupService;
import com.envisione.progressiveskills.server.social.ProgressionCurrencyTransferService;
import com.envisione.progressiveskills.server.social.PartyContributionService;
import com.envisione.progressiveskills.server.social.SocialProviderRuntime;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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

import java.time.Instant;
import java.util.Locale;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class SocialCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private SocialCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("party")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> partyCreate(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> partyInvite(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("accept").executes(context -> partyAccept(context.getSource())))
                        .then(Commands.literal("leave").executes(context -> partyLeave(context.getSource())))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> partyKick(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("owner")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> partyOwner(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("disband").executes(context -> partyDisband(context.getSource())))
                        .then(Commands.literal("status").executes(context -> partyStatus(context.getSource())))
                        .then(Commands.literal("ready")
                                .then(Commands.argument("ready", BoolArgumentType.bool())
                                        .executes(context -> partyReady(context.getSource(),
                                                BoolArgumentType.getBool(context, "ready")))))
                        .then(Commands.literal("readiness").executes(context -> partyReadiness(context.getSource()))))
                .then(Commands.literal("privacy")
                        .then(Commands.literal("status").executes(context -> privacyStatus(context.getSource())))
                        .then(Commands.literal("visibility")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> privacyVisibility(context.getSource(),
                                                StringArgumentType.getString(context, "value")))))
                        .then(privacyToggle("role"))
                        .then(privacyToggle("build"))
                        .then(privacyToggle("resources"))
                        .then(privacyToggle("cooldowns"))
                        .then(privacyToggle("leaderboard")))
                .then(Commands.literal("contribution")
                        .then(Commands.literal("share").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("source", ResourceLocationArgument.id())
                                        .then(Commands.argument("total", LongArgumentType.longArg(0))
                                                .executes(context -> contribution(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "source"),
                                                        LongArgumentType.getLong(context, "total"))))))
                        .then(Commands.literal("receipts").executes(context -> receipts(context.getSource()))))
                .then(Commands.literal("mentor")
                        .then(Commands.literal("offer")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> mentorOffer(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("accept").executes(context -> mentorAccept(context.getSource())))
                        .then(Commands.literal("remove").executes(context -> mentorRemove(context.getSource())))
                        .then(Commands.literal("status").executes(context -> mentorStatus(context.getSource()))))
                .then(Commands.literal("transfer")
                        .then(Commands.literal("offer")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", ResourceLocationArgument.id())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                        .executes(context -> transferOffer(context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                ResourceLocationArgument.getId(context, "currency"),
                                                                LongArgumentType.getLong(context, "amount")))))))
                        .then(Commands.literal("accept").executes(context -> transferAccept(context.getSource())))
                        .then(Commands.literal("wallet")
                                .then(Commands.argument("currency", ResourceLocationArgument.id())
                                        .executes(context -> wallet(context.getSource(),
                                                ResourceLocationArgument.getId(context, "currency")))))
                        .then(Commands.literal("grant").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", ResourceLocationArgument.id())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                        .executes(context -> walletGrant(context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                ResourceLocationArgument.getId(context, "currency"),
                                                                LongArgumentType.getLong(context, "amount"))))))))
                .then(Commands.literal("sharedchallenge").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("challenge", ResourceLocationArgument.id())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .then(Commands.argument("goal", LongArgumentType.longArg(1))
                                                .executes(context -> sharedChallenge(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "challenge"),
                                                        LongArgumentType.getLong(context, "amount"),
                                                        LongArgumentType.getLong(context, "goal")))))))
                .then(Commands.literal("season")
                        .then(Commands.literal("status").executes(context -> seasonStatus(context.getSource())))
                        .then(Commands.literal("score").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("board", ResourceLocationArgument.id())
                                                .then(Commands.argument("score", LongArgumentType.longArg(0))
                                                        .executes(context -> seasonScore(context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                ResourceLocationArgument.getId(context, "board"),
                                                                LongArgumentType.getLong(context, "score")))))))
                        .then(Commands.literal("leaderboard")
                                .then(Commands.argument("board", ResourceLocationArgument.id())
                                        .executes(context -> leaderboard(context.getSource(),
                                                ResourceLocationArgument.getId(context, "board"), 10))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                                .executes(context -> leaderboard(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "board"),
                                                        IntegerArgumentType.getInteger(context, "limit"))))))
                        .then(Commands.literal("rollover").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("season", ResourceLocationArgument.id())
                                        .then(Commands.argument("epoch", LongArgumentType.longArg(1))
                                                .executes(context -> rollover(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "season"),
                                                        LongArgumentType.getLong(context, "epoch"))))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> privacyToggle(String name) {
        return Commands.literal(name).then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(context -> privacyToggle(context.getSource(), name,
                        BoolArgumentType.getBool(context, "enabled"))));
    }

    private static int partyCreate(CommandSourceStack source, String name) {
        return run(source, player -> {
            var party = parties(source).create(player.getUUID(), name);
            success(source, "Party created. " + party.stableId() + ".");
        });
    }

    private static int partyInvite(CommandSourceStack source, ServerPlayer target) {
        return run(source, player -> {
            parties(source).invite(player.getUUID(), target.getUUID());
            success(source, "Party invitation sent to " + target.getGameProfile().getName() + ".");
            target.sendSystemMessage(Component.literal(PREFIX + player.getGameProfile().getName()
                    + " invited you. Run ps party accept."));
        });
    }

    private static int partyAccept(CommandSourceStack source) {
        return run(source, player -> success(source, "Joined "
                + parties(source).accept(player.getUUID()).name() + "."));
    }

    private static int partyLeave(CommandSourceStack source) {
        return run(source, player -> {
            parties(source).leave(player.getUUID());
            success(source, "Left the party.");
        });
    }

    private static int partyKick(CommandSourceStack source, ServerPlayer target) {
        return run(source, player -> {
            parties(source).kick(player.getUUID(), target.getUUID());
            success(source, "Removed " + target.getGameProfile().getName() + ".");
        });
    }

    private static int partyOwner(CommandSourceStack source, ServerPlayer target) {
        return run(source, player -> {
            parties(source).transferOwnership(player.getUUID(), target.getUUID());
            success(source, "Party ownership transferred.");
        });
    }

    private static int partyDisband(CommandSourceStack source) {
        return run(source, player -> {
            parties(source).disband(player.getUUID());
            success(source, "Party disbanded.");
        });
    }

    private static int partyStatus(CommandSourceStack source) {
        return run(source, player -> {
            var party = parties(source).party(player.getUUID()).orElseThrow(
                    () -> new IllegalStateException("Player is not in a party"));
            success(source, party.name() + ". Owner " + party.owner() + ". Members " + party.members() + ".");
        });
    }

    private static int partyReady(CommandSourceStack source, boolean ready) {
        return run(source, player -> {
            String build = BuildShareCode.encode(CreatorRuntime.currentBuild(player));
            parties(source).setReadiness(player.getUUID(), new PartyReadiness(
                    ready, "member", build, "available", "available", Instant.now()));
            success(source, "Party readiness " + ready + ".");
        });
    }

    private static int partyReadiness(CommandSourceStack source) {
        return run(source, player -> parties(source).readiness(player.getUUID()).forEach((id, value) ->
                success(source, id + ". Ready " + value.ready() + ". Role " + value.role()
                        + ". Build " + value.build() + ". Resources " + value.resources()
                        + ". Cooldowns " + value.cooldowns() + ".")));
    }

    private static int privacyStatus(CommandSourceStack source) {
        return run(source, player -> success(source, data(source).privacy(player.getUUID()).toString() + "."));
    }

    private static int privacyVisibility(CommandSourceStack source, String value) {
        return run(source, player -> {
            PrivacySettings before = data(source).privacy(player.getUUID());
            PrivacySettings.Visibility visibility = PrivacySettings.Visibility.valueOf(
                    value.toUpperCase(Locale.ROOT));
            data(source).setPrivacy(player.getUUID(), new PrivacySettings(
                    visibility, before.shareRole(), before.shareBuild(), before.shareResources(),
                    before.shareCooldowns(), before.leaderboardOptIn()));
            success(source, "Profile visibility " + visibility + ".");
        });
    }

    private static int privacyToggle(CommandSourceStack source, String name, boolean enabled) {
        return run(source, player -> {
            PrivacySettings value = data(source).privacy(player.getUUID());
            PrivacySettings next = new PrivacySettings(value.profileVisibility(),
                    name.equals("role") ? enabled : value.shareRole(),
                    name.equals("build") ? enabled : value.shareBuild(),
                    name.equals("resources") ? enabled : value.shareResources(),
                    name.equals("cooldowns") ? enabled : value.shareCooldowns(),
                    name.equals("leaderboard") ? enabled : value.leaderboardOptIn());
            data(source).setPrivacy(player.getUUID(), next);
            success(source, "Privacy " + name + " " + enabled + ".");
        });
    }

    private static int contribution(CommandSourceStack source, ResourceLocation id, long total) {
        return run(source, player -> {
            var party = parties(source).party(player.getUUID()).orElseThrow();
            var skill = SkillRuntime.catalog().flatMap(catalog -> catalog.skill(id)).orElseThrow(
                    () -> new IllegalArgumentException("Unknown shared XP skill " + id));
            var weights = new java.util.LinkedHashMap<java.util.UUID, Long>();
            party.members().forEach(member -> weights.put(member, 1L));
            var receipt = PartyContributionService.award(
                    player, id, skill, total, weights,
                    "Weighted party allocation with configured mentor catchup bonuses");
            success(source, "Contribution receipt " + receipt.receiptId() + ". Shares " + receipt.shares() + ".");
        });
    }

    private static int receipts(CommandSourceStack source) {
        return run(source, player -> {
            var party = parties(source).party(player.getUUID()).orElseThrow(
                    () -> new IllegalStateException("Player is not in a party"));
            data(source).receiptsForGroup(party.stableId()).forEach(receipt ->
                    success(source, receipt.receiptId() + ". " + receipt.source() + ". Total "
                            + receipt.total() + ". Shares " + receipt.shares() + "."));
        });
    }

    private static int mentorOffer(CommandSourceStack source, ServerPlayer target) {
        return run(source, player -> {
            data(source).offerMentor(player.getUUID(), target.getUUID());
            success(source, "Mentor offer sent.");
            target.sendSystemMessage(Component.literal(PREFIX + "Mentor offer received. Run ps mentor accept."));
        });
    }

    private static int mentorAccept(CommandSourceStack source) {
        return run(source, player -> {
            data(source).acceptMentor(player.getUUID());
            success(source, "Mentor accepted.");
        });
    }

    private static int mentorRemove(CommandSourceStack source) {
        return run(source, player -> {
            data(source).removeMentor(player.getUUID());
            success(source, "Mentor link removed.");
        });
    }

    private static int mentorStatus(CommandSourceStack source) {
        return run(source, player -> success(source, "Mentor "
                + data(source).mentor(player.getUUID()).map(Object::toString).orElse("none") + "."));
    }

    private static int transferOffer(
            CommandSourceStack source,
            ServerPlayer target,
            ResourceLocation currency,
            long amount
    ) {
        return run(source, player -> {
            ProgressionCurrencyTransferService.offer(player, target, currency, amount);
            success(source, "Transfer offer sent.");
            target.sendSystemMessage(Component.literal(PREFIX + "Transfer offer received. Run ps transfer accept."));
        });
    }

    private static int transferAccept(CommandSourceStack source) {
        return run(source, player -> {
            var offer = ProgressionCurrencyTransferService.accept(player);
            success(source, "Accepted " + offer.amount() + " " + offer.currency() + ".");
        });
    }

    private static int wallet(CommandSourceStack source, ResourceLocation currency) {
        return run(source, player -> success(source, currency + " balance "
                + ProgressionCurrencyTransferService.balance(player, currency) + "."));
    }

    private static int walletGrant(
            CommandSourceStack source,
            ServerPlayer target,
            ResourceLocation currency,
            long amount
    ) {
        try {
            long value = ProgressionCurrencyTransferService.grant(target, currency, amount);
            success(source, target.getGameProfile().getName() + " balance " + value + ".");
            return 1;
        } catch (RuntimeException exception) {
            return failed(source, exception);
        }
    }

    private static int sharedChallenge(
            CommandSourceStack source,
            ResourceLocation challenge,
            long amount,
            long goal
    ) {
        return run(source, player -> success(source, "Shared challenge progress "
                + parties(source).progressSharedChallenge(player.getUUID(), challenge, amount, goal)
                + " of " + goal + "."));
    }

    private static int seasonStatus(CommandSourceStack source) {
        success(source, data(source).seasonStatus().toString() + ".");
        return 1;
    }

    private static int seasonScore(
            CommandSourceStack source,
            ServerPlayer player,
            ResourceLocation board,
            long score
    ) {
        try {
            data(source).recordScore(player.getUUID(), board, score);
            success(source, "Leaderboard score saved.");
            return 1;
        } catch (RuntimeException exception) {
            return failed(source, exception);
        }
    }

    private static int leaderboard(CommandSourceStack source, ResourceLocation board, int limit) {
        var values = data(source).leaderboard(board, limit);
        if (values.isEmpty()) {
            success(source, "Leaderboard is empty.");
        }
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            success(source, (index + 1) + ". " + value.getKey() + ". " + value.getValue() + ".");
        }
        return 1;
    }

    private static int rollover(CommandSourceStack source, ResourceLocation season, long epoch) {
        try {
            data(source).rollover(season, epoch);
            success(source, "Season rollover committed.");
            return 1;
        } catch (RuntimeException exception) {
            return failed(source, exception);
        }
    }

    private static int run(CommandSourceStack source, PlayerOperation operation) {
        try {
            operation.run(source.getPlayerOrException());
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException exception) {
            return failed(source, exception);
        }
    }

    private static MultiplayerSavedData data(CommandSourceStack source) {
        return MultiplayerSavedData.get(source.getServer());
    }

    private static PartyProvider parties(CommandSourceStack source) {
        return SocialProviderRuntime.parties(source.getServer());
    }

    private static int failed(CommandSourceStack source, Exception exception) {
        failure(source, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        return 0;
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    @FunctionalInterface
    private interface PlayerOperation {
        void run(ServerPlayer player);
    }
}
