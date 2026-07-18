package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.tree.TreeProgression;
import com.envisione.progressiveskills.server.tree.TreeRuntime;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class TreeCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private TreeCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pskills")
                .then(Commands.literal("tree")
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("info")
                                .then(treeArgument().executes(context -> info(
                                        context.getSource(), tree(context)
                                ))))
                        .then(Commands.literal("preview")
                                .then(Commands.literal("buy")
                                        .then(treeAndNode((context, treeId, nodeId) ->
                                                previewBuy(context.getSource(), treeId, nodeId))))
                                .then(Commands.literal("refund")
                                        .then(treeAndNode((context, treeId, nodeId) ->
                                                previewRefund(context.getSource(), treeId, nodeId))))
                                .then(Commands.literal("respec")
                                        .then(treeArgument().executes(context -> previewRespec(
                                                context.getSource(), tree(context)
                                        )))))
                        .then(Commands.literal("buy")
                                .then(treeAndNode((context, treeId, nodeId) ->
                                        buy(context.getSource(), treeId, nodeId))))
                        .then(Commands.literal("refund")
                                .then(Commands.argument("tree", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> suggestTrees(builder))
                                        .then(Commands.argument("node", ResourceLocationArgument.id())
                                                .suggests(TreeCommands::suggestNodes)
                                                .then(Commands.argument("digest", StringArgumentType.word())
                                                        .executes(context -> refund(
                                                                context.getSource(), tree(context), node(context),
                                                                StringArgumentType.getString(context, "digest")
                                                        ))))))
                        .then(Commands.literal("respec")
                                .then(Commands.argument("tree", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> suggestTrees(builder))
                                        .then(Commands.argument("digest", StringArgumentType.word())
                                                .executes(context -> respec(
                                                        context.getSource(), tree(context),
                                                        StringArgumentType.getString(context, "digest")
                                                )))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>
    treeArgument() {
        return Commands.argument("tree", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestTrees(builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>
    treeAndNode(TreeAction action) {
        return Commands.argument("tree", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestTrees(builder))
                .then(Commands.argument("node", ResourceLocationArgument.id())
                        .suggests(TreeCommands::suggestNodes)
                        .executes(context -> action.run(context, tree(context), node(context))));
    }

    private static int list(CommandSourceStack source) {
        try {
            var catalog = TreeRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Tree catalog is unavailable")
            );
            success(source, "Trees " + catalog.trees().size() + ".");
            catalog.trees().values().forEach(tree -> success(source,
                    tree.id() + ". " + tree.presentation().display().fallback() + ". Nodes "
                            + tree.nodes().size() + ". Currency " + tree.currency() + ". "
                            + (tree.enabled() ? "Enabled." : "Disabled.")));
            return catalog.trees().size();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(source, exception);
        }
    }

    private static int info(CommandSourceStack source, ResourceLocation treeId) {
        try {
            var tree = TreeRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Tree catalog is unavailable")
            ).tree(treeId).orElseThrow(() -> new IllegalArgumentException("Unknown tree " + treeId));
            success(source, tree.id() + ". " + tree.presentation().display().fallback() + ". Scope "
                    + tree.scope().serializedName() + ". Currency " + tree.currency()
                    + ". Policy " + tree.dependencyPolicy().serializedName() + ".");
            for (var node : tree.nodes()) {
                success(source, node.id() + ". " + node.presentation().display().fallback()
                        + ". Cost " + node.cost() + ". Grid " + node.row() + ", " + node.column()
                        + ". Requires " + node.requires().size() + ". Any " + node.requiresAny().size() + ".");
            }
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return fail(source, exception);
        }
    }

    private static int previewBuy(
            CommandSourceStack source,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        try {
            TreeProgression.PurchasePreview preview = TreeRuntime.previewPurchase(
                    source.getPlayerOrException(), treeId, nodeId
            );
            if (preview.allowed()) {
                success(source, "Purchase available. Cost " + preview.cost() + " " + preview.currency()
                        + ". Balance " + preview.balance() + ".");
                return 1;
            }
            failure(source, "Purchase locked. " + String.join(". ", preview.blockers()) + ".");
            return 0;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int buy(
            CommandSourceStack source,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var result = TreeRuntime.purchase(
                    player, treeId, nodeId, commandKey("buy")
            );
            return report(source, result.transaction(), "Purchased " + nodeId + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int previewRefund(
            CommandSourceStack source,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        try {
            return reportPreview(source, TreeRuntime.previewRefund(
                    source.getPlayerOrException(), treeId, nodeId
            ));
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int refund(
            CommandSourceStack source,
            ResourceLocation treeId,
            ResourceLocation nodeId,
            String digest
    ) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var result = TreeRuntime.refund(
                    player, treeId, nodeId, digest, commandKey("refund")
            );
            return report(source, result.transaction(), "Refunded "
                    + result.preview().affectedNodes().size() + " nodes.");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int previewRespec(CommandSourceStack source, ResourceLocation treeId) {
        try {
            return reportPreview(source, TreeRuntime.previewRespec(
                    source.getPlayerOrException(), treeId
            ));
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int respec(CommandSourceStack source, ResourceLocation treeId, String digest) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var result = TreeRuntime.respec(player, treeId, digest, commandKey("respec"));
            return report(source, result.transaction(), "Respecced " + treeId + ". Nodes "
                    + result.preview().affectedNodes().size() + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int reportPreview(CommandSourceStack source, TreeProgression.RefundPreview preview) {
        if (!preview.allowed()) {
            failure(source, "Refund unavailable. " + String.join(". ", preview.blockers()) + ".");
            return 0;
        }
        success(source, "Refund affects " + preview.affectedNodes().size() + " nodes. Order "
                + String.join(", ", preview.affectedNodes().stream().map(ResourceLocation::toString).toList()) + ".");
        preview.refundBalances().forEach((currency, amount) ->
                success(source, "Refund " + amount + " " + currency + "."));
        success(source, "Confirmation digest " + preview.digest() + ".");
        return 1;
    }

    private static int report(CommandSourceStack source, TransactionResult result, String message) {
        if (!result.status().committed()) {
            failure(source, result.diagnosticCode() + ". " + result.message());
            return 0;
        }
        success(source, message + " Transaction " + result.transactionId() + ".");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestTrees(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                TreeRuntime.catalog().stream().flatMap(catalog -> catalog.trees().keySet().stream())
                        .map(ResourceLocation::toString),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestNodes(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        try {
            ResourceLocation treeId = tree(context);
            return SharedSuggestionProvider.suggest(
                    TreeRuntime.catalog().stream().flatMap(catalog -> catalog.tree(treeId).stream())
                            .flatMap(value -> value.nodes().stream()).map(value -> value.id().toString()),
                    builder
            );
        } catch (RuntimeException exception) {
            return builder.buildFuture();
        }
    }

    private static ResourceLocation tree(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "tree");
    }

    private static ResourceLocation node(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "node");
    }

    private static IdempotencyKey commandKey(String action) {
        return new IdempotencyKey("phase10/command/" + action + "/" + UUID.randomUUID());
    }

    private static int fail(CommandSourceStack source, Throwable throwable) {
        failure(source, safeMessage(throwable));
        return 0;
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface TreeAction {
        int run(CommandContext<CommandSourceStack> context, ResourceLocation treeId, ResourceLocation nodeId);
    }
}
