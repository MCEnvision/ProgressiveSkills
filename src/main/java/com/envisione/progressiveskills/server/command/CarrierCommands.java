package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.carrier.CarrierCurrencyAction;
import com.envisione.progressiveskills.common.carrier.CarrierDefinition;
import com.envisione.progressiveskills.common.carrier.CarrierSkillLevelAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierTreeRespecAction;
import com.envisione.progressiveskills.common.carrier.CarrierUseAction;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.server.carrier.BehaviorArchiveSavedData;
import com.envisione.progressiveskills.server.carrier.CarrierDeliveryService;
import com.envisione.progressiveskills.server.carrier.CarrierStackService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class CarrierCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private CarrierCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pskills")
                .then(Commands.literal("item")
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("info")
                                .then(carrierArgument("item_def").executes(context -> info(
                                        context.getSource(), carrierId(context, "item_def")
                                ))))
                        .then(Commands.literal("held")
                                .executes(context -> held(context.getSource())))
                        .then(Commands.literal("archive")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("status")
                                        .executes(context -> archiveStatus(context.getSource())))
                                .then(Commands.literal("verify")
                                        .executes(context -> archiveVerify(context.getSource()))))
                        .then(Commands.literal("migrate")
                                .then(Commands.literal("held")
                                        .then(Commands.literal("preview")
                                                .executes(context -> migratePreview(context.getSource())))
                                        .then(Commands.literal("confirm")
                                                .then(Commands.argument("digest", StringArgumentType.word())
                                                        .suggests((context, builder) -> suggestMigrationDigest(
                                                                context.getSource(), builder
                                                        ))
                                                        .executes(context -> migrateConfirm(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "digest")
                                                        )))))))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(carrierArgument("item_def")
                                        .executes(context -> give(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                carrierId(context, "item_def"),
                                                1
                                        ))
                                        .then(Commands.argument(
                                                        "count",
                                                        IntegerArgumentType.integer(
                                                                1, CarrierDeliveryService.MAX_COMMAND_COUNT
                                                        )
                                                )
                                                .executes(context -> give(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        carrierId(context, "item_def"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                ))))))
                .then(Commands.literal("claim")
                        .then(Commands.literal("list")
                                .executes(context -> claimList(context.getSource())))
                        .then(Commands.literal("take")
                                .then(Commands.literal("all")
                                        .executes(context -> claimTakeAll(context.getSource())))
                                .then(Commands.argument("claim_id", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestClaims(
                                                context.getSource(), builder
                                        ))
                                        .executes(context -> claimTake(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "claim_id")
                                        ))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> carrierArgument(
            String name
    ) {
        return Commands.argument(name, ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestCarriers(builder));
    }

    private static int list(CommandSourceStack source) {
        try {
            var definitions = CarrierDeliveryService.liveDefinitions();
            success(source, "Live carriers " + definitions.size() + ".");
            definitions.values().forEach(definition -> success(source,
                    definition.id() + ". " + definition.presentation().display().fallback()
                            + ". Kind " + definition.carrier().serializedName()
                            + ". Behavior " + definition.behaviorVersion() + "."));
            return definitions.size();
        } catch (RuntimeException exception) {
            return fail(source, exception);
        }
    }

    private static int info(CommandSourceStack source, ResourceLocation definitionId) {
        try {
            CarrierDefinition definition = CarrierDeliveryService.liveDefinition(definitionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown live carrier " + definitionId
                    ));
            success(source, definition.id() + ". " + definition.presentation().display().fallback()
                    + ". Kind " + definition.carrier().serializedName()
                    + ". Enabled " + definition.enabled() + ".");
            success(source, "Behavior " + definition.behaviorVersion()
                    + ". Digest " + definition.behaviorDigest() + ".");
            success(source, "Migration " + definition.migrationPolicy().serializedName()
                    + ". Binding " + definition.bindPolicy().serializedName()
                    + ". Delivery " + definition.deliveryPolicy().serializedName() + ".");
            success(source, "Rarity " + definition.rarity().serializedName()
                    + ". Glint " + definition.glint()
                    + ". Stack size " + definition.stackSize()
                    + ". Charges " + definition.charges()
                    + ". Cooldown ticks " + definition.cooldownTicks() + ".");
            definition.useActions().forEach(action -> success(source, actionText(action)));
            return 1;
        } catch (RuntimeException exception) {
            return fail(source, exception);
        }
    }

    private static int held(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            CarrierStackService.Inspection inspection = CarrierStackService.inspect(player, stack);
            if (inspection.identity().isEmpty() || inspection.state().isEmpty()) {
                failure(source, inspection.message());
                return 0;
            }
            var identity = inspection.identity().orElseThrow();
            var state = inspection.state().orElseThrow();
            success(source, "Carrier " + identity.definitionId()
                    + ". Digest " + identity.behaviorDigest() + ".");
            success(source, "Behavior " + state.behaviorVersion()
                    + ". Charges " + state.charges()
                    + ". Instance " + state.instanceId()
                    + ". Use counter " + state.useCounter() + ".");
            success(source, "Created from pack " + state.creationPackDigest()
                    + ". Bound owner " + state.boundOwner().map(UUID::toString).orElse("none")
                    + ". Migration " + state.migrationMarker().orElse("none") + ".");
            success(source, "Archived behavior "
                    + inspection.archivedBehavior().map(value -> value.digest()).orElse("missing")
                    + ". Live behavior "
                    + inspection.currentBehavior().map(value -> value.digest()).orElse("missing") + ".");
            inspection.resolution().ifPresent(resolution -> {
                success(source, "Resolution " + resolution.code().name().toLowerCase(Locale.ROOT)
                        + ". " + resolution.message() + ".");
                resolution.warning().ifPresent(warning -> success(source, "Warning " + warning + "."));
            });
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int give(
            CommandSourceStack source,
            ServerPlayer target,
            ResourceLocation definitionId,
            int count
    ) {
        try {
            CarrierDeliveryService.AcquisitionResult result = CarrierDeliveryService.acquire(
                    target, definitionId, count
            );
            if (!result.accepted()) {
                failure(source, result.message());
                return 0;
            }
            success(source, result.message() + ". Target " + target.getGameProfile().getName() + ".");
            return result.requested();
        } catch (RuntimeException exception) {
            return fail(source, exception);
        }
    }

    private static int claimList(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var data = player.getData(PsDataAttachments.PLAYER_DATA);
            if (!data.active()) {
                throw new IllegalStateException("Player progression data is unavailable");
            }
            var claims = data.pendingClaims();
            success(source, "Pending claims " + claims.size() + ".");
            claims.forEach(claim -> success(source,
                    claim.claimId() + ". Carrier " + claim.identity().definitionId()
                            + ". Kind " + claim.kind().serializedName()
                            + ". Charges " + claim.state().charges()
                            + ". Created " + claim.createdAt()
                            + ". Reason " + claim.reason() + "."));
            return claims.size();
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int claimTake(CommandSourceStack source, String claimText) {
        try {
            UUID claimId = requireClaimId(claimText);
            var result = CarrierDeliveryService.takeClaim(source.getPlayerOrException(), claimId);
            if (!result.accepted()) {
                failure(source, result.message());
                return 0;
            }
            success(source, result.message() + ".");
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int claimTakeAll(CommandSourceStack source) {
        try {
            var result = CarrierDeliveryService.takeAllClaims(source.getPlayerOrException());
            if (!result.accepted()) {
                failure(source, result.message());
                return 0;
            }
            success(source, result.message() + ".");
            return result.delivered();
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int archiveStatus(CommandSourceStack source) {
        try {
            BehaviorArchiveSavedData.Status status = BehaviorArchiveSavedData
                    .get(source.getServer()).status();
            success(source, "Behavior archive entries " + status.entries()
                    + " of " + status.entryCapacity()
                    + ". Bytes " + status.bytes() + " of " + status.byteCapacity()
                    + ". Reliable " + status.reliable() + ".");
            status.quarantine().ifPresent(issue -> failure(source, "Archive quarantine " + issue));
            return status.reliable() ? 1 : 0;
        } catch (RuntimeException exception) {
            return fail(source, exception);
        }
    }

    private static int archiveVerify(CommandSourceStack source) {
        try {
            BehaviorArchiveSavedData.Verification verification = BehaviorArchiveSavedData
                    .get(source.getServer()).verify();
            if (!verification.valid()) {
                failure(source, "Behavior archive verification failed after "
                        + verification.checkedEntries() + " entries. "
                        + verification.issue().orElse("Unknown archive issue"));
                return 0;
            }
            success(source, "Behavior archive verified "
                    + verification.checkedEntries() + " entries.");
            return 1;
        } catch (RuntimeException exception) {
            return fail(source, exception);
        }
    }

    private static int migratePreview(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var preview = CarrierStackService.migratePreview(player, player.getMainHandItem());
            if (!preview.allowed()) {
                failure(source, preview.message());
                return 0;
            }
            success(source, preview.message() + ". Confirmation digest "
                    + preview.previewDigest() + ".");
            success(source, "Next behavior " + preview.behavior().orElseThrow().behaviorVersion()
                    + ". Next charges " + preview.state().orElseThrow().charges()
                    + ". Behavior digest " + preview.identity().orElseThrow().behaviorDigest() + ".");
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int migrateConfirm(CommandSourceStack source, String digest) {
        try {
            requireDigest(digest);
            ServerPlayer player = source.getPlayerOrException();
            var result = CarrierStackService.migrate(player, player.getMainHandItem(), digest);
            if (!result.migrated()) {
                failure(source, result.message());
                return 0;
            }
            player.getInventory().setChanged();
            success(source, result.message() + ".");
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    static UUID requireClaimId(String value) {
        Objects.requireNonNull(value, "value");
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("Pending claim id is not canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid pending claim id", exception);
        }
    }

    static String requireDigest(String value) {
        Objects.requireNonNull(value, "value");
        if (value.length() != 64) {
            throw new IllegalArgumentException("Migration digest must contain 64 hexadecimal characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < '0' || character > '9') && (character < 'a' || character > 'f')) {
                throw new IllegalArgumentException("Migration digest must use lowercase hexadecimal characters");
            }
        }
        return value;
    }

    static String actionText(CarrierUseAction action) {
        Objects.requireNonNull(action, "action");
        String detail;
        if (action instanceof CarrierSkillXpAction xp) {
            detail = "Skill " + xp.skill() + ". Fixed point amount " + xp.amountUnits();
        } else if (action instanceof CarrierSkillLevelAction level) {
            detail = "Skill " + level.skill() + ". Levels " + level.levels();
        } else if (action instanceof CarrierCurrencyAction currency) {
            detail = "Currency " + currency.currency() + ". Amount " + currency.amount();
        } else {
            CarrierTreeRespecAction tree = (CarrierTreeRespecAction) action;
            detail = "Tree " + tree.tree();
        }
        return "Action " + action.id() + ". Type " + action.type().serializedName()
                + ". " + detail + ". Consumes " + action.consume() + ".";
    }

    private static CompletableFuture<Suggestions> suggestCarriers(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                CarrierDeliveryService.liveDefinitions().keySet().stream().map(ResourceLocation::toString),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestClaims(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        try {
            return SharedSuggestionProvider.suggest(
                    source.getPlayerOrException().getData(PsDataAttachments.PLAYER_DATA)
                            .pendingClaims().stream().map(claim -> claim.claimId().toString()),
                    builder
            );
        } catch (Exception exception) {
            return Suggestions.empty();
        }
    }

    private static CompletableFuture<Suggestions> suggestMigrationDigest(
            CommandSourceStack source,
            SuggestionsBuilder builder
    ) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var preview = CarrierStackService.migratePreview(player, player.getMainHandItem());
            return preview.allowed()
                    ? SharedSuggestionProvider.suggest(new String[]{preview.previewDigest()}, builder)
                    : Suggestions.empty();
        } catch (Exception exception) {
            return Suggestions.empty();
        }
    }

    private static ResourceLocation carrierId(
            CommandContext<CommandSourceStack> context,
            String name
    ) {
        return ResourceLocationArgument.getId(context, name);
    }

    private static int fail(CommandSourceStack source, Throwable throwable) {
        failure(source, safeMessage(throwable));
        return 0;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }
}
