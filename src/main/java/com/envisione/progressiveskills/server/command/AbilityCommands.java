package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.ability.AbilityAttributeEffect;
import com.envisione.progressiveskills.common.ability.AbilityCurrencyCost;
import com.envisione.progressiveskills.common.ability.AbilityHealAction;
import com.envisione.progressiveskills.common.ability.AbilityMessageAction;
import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.common.ability.AbilityVanillaEffectAction;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.server.ability.AbilityRuntime;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
public final class AbilityCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private AbilityCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pskills")
                .then(Commands.literal("ability")
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("info")
                                .then(abilityArgument("ability").executes(context -> info(
                                        context.getSource(), abilityId(context)))))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("assign")
                                .then(abilityArgument("ability")
                                        .then(slotArgument().executes(context -> assign(
                                                context.getSource(), abilityId(context), slot(context))))))
                        .then(Commands.literal("unassign")
                                .then(slotArgument().executes(context -> unassign(
                                        context.getSource(), slot(context)))))
                        .then(Commands.literal("select")
                                .then(slotArgument().executes(context -> select(
                                        context.getSource(), slot(context)))))
                        .then(Commands.literal("toggle")
                                .then(abilityArgument("ability").executes(context -> toggle(
                                        context.getSource(), abilityId(context)))))
                        .then(Commands.literal("activate")
                                .then(slotArgument().executes(context -> activate(
                                        context.getSource(), slot(context)))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> abilityArgument(
            String name
    ) {
        return Commands.argument(name, ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestAbilities(builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> slotArgument() {
        return Commands.argument("slot", IntegerArgumentType.integer(1, AbilityState.SLOT_COUNT));
    }

    private static int list(CommandSourceStack source) {
        try {
            var catalog = AbilityRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Ability catalog is unavailable"));
            ServerPlayer player = source.getPlayerOrException();
            AbilityState state = AbilityRuntime.state(player);
            success(source, "Abilities " + catalog.abilities().size() + ". Owned "
                    + state.ownedAbilities().size() + ". Assigned " + state.assignments().size() + ".");
            catalog.abilities().values().forEach(ability -> {
                String ownership = state.ownedAbilities().contains(ability.id()) ? "Owned" : "Locked";
                String assignment = state.assignments().entrySet().stream()
                        .filter(entry -> entry.getValue().equals(ability.id()))
                        .map(entry -> " Slot " + (AbilityState.slotIndex(entry.getKey()) + 1) + ".")
                        .findFirst().orElse("");
                success(source, ability.id() + ". " + ability.presentation().display().fallback()
                        + ". Kind " + ability.kind().serializedName() + ". State " + ownership
                        + "." + assignment);
            });
            return catalog.abilities().size();
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int info(CommandSourceStack source, ResourceLocation abilityId) {
        try {
            var definition = AbilityRuntime.catalog().flatMap(catalog -> catalog.ability(abilityId))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ability " + abilityId));
            success(source, definition.id() + ". " + definition.presentation().display().fallback()
                    + ". Kind " + definition.kind().serializedName() + ". Enabled "
                    + definition.enabled() + ". Slot allowed " + definition.slotAllowed() + ".");
            success(source, "Target " + definition.targeting().mode().serializedName()
                    + ". Range " + definition.targeting().range() + ". Line of sight "
                    + definition.targeting().lineOfSight() + ".");
            success(source, "Cooldown group " + definition.cooldownGroup() + ". Cooldown ticks "
                    + definition.cooldownTicks() + ". Charges " + definition.maxCharges()
                    + ". Recharge ticks " + definition.rechargeTicks() + ".");
            if (definition.costs().isEmpty()) {
                success(source, "Costs none.");
            }
            definition.costs().forEach(cost -> success(source,
                    "Cost " + cost.id() + ". Type " + cost.type().serializedName()
                            + ". Amount " + cost.amount()
                            + (cost instanceof AbilityCurrencyCost currency
                            ? ". Currency " + currency.currency() : "") + "."));
            if (definition.persistentEffects().isEmpty()) {
                success(source, "Persistent effects none.");
            }
            definition.persistentEffects().forEach(effect -> success(source,
                    "Persistent effect " + effect.id() + ". Type " + effect.type().serializedName()
                            + ". Target " + effect.targetId() + ". Operation "
                            + (effect instanceof AbilityAttributeEffect attribute
                            ? attribute.operation().serializedName() : "owned") + ". Value "
                            + effect.value() + "."));
            if (definition.actions().isEmpty()) {
                success(source, "Actions none.");
            }
            definition.actions().forEach(action -> reportAction(source, action));
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int status(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            AbilityState state = AbilityRuntime.state(player);
            success(source, "Selected slot " + state.selectedSlot()
                    .map(slot -> Integer.toString(AbilityState.slotIndex(slot) + 1)).orElse("none") + ".");
            for (int slot = 0; slot < AbilityState.SLOT_COUNT; slot++) {
                ResourceLocation slotId = AbilityState.slotId(slot);
                success(source, "Slot " + (slot + 1) + ". Ability "
                        + state.assignedAbility(slotId).map(Object::toString).orElse("empty") + ".");
            }
            var catalog = AbilityRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Ability catalog is unavailable"));
            for (ResourceLocation abilityId : state.ownedAbilities()) {
                var definition = catalog.ability(abilityId);
                if (definition.isEmpty()) {
                    success(source, "Owned ability " + abilityId + ". Definition missing.");
                    continue;
                }
                var ability = definition.orElseThrow();
                var charge = state.charges().get(abilityId);
                String chargeText = charge == null ? "not used"
                        : charge.current() + " of " + charge.maximum();
                success(source, "Ability " + abilityId + ". Toggle " + state.toggleOn(abilityId)
                        + ". Charges " + chargeText + ". Cooldown remaining "
                        + state.cooldownRemaining(
                        ability.cooldownGroup(), player.serverLevel().getGameTime()) + " ticks.");
            }
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int assign(CommandSourceStack source, ResourceLocation abilityId, int slot) {
        try {
            var result = AbilityRuntime.assign(
                    source.getPlayerOrException(), abilityId, slot, commandKey("assign"));
            return report(source, result.accepted(), result.message(),
                    "Assigned " + abilityId + " to slot " + (slot + 1) + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int unassign(CommandSourceStack source, int slot) {
        try {
            var result = AbilityRuntime.unassign(
                    source.getPlayerOrException(), slot, commandKey("unassign"));
            return report(source, result.accepted(), result.message(),
                    "Unassigned slot " + (slot + 1) + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int select(CommandSourceStack source, int slot) {
        try {
            var result = AbilityRuntime.select(
                    source.getPlayerOrException(), slot, commandKey("select"));
            return report(source, result.accepted(), result.message(),
                    "Selected slot " + (slot + 1) + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int toggle(CommandSourceStack source, ResourceLocation abilityId) {
        try {
            var result = AbilityRuntime.toggle(
                    source.getPlayerOrException(), abilityId, commandKey("toggle"));
            return report(source, result.accepted(), result.message(), "Toggled " + abilityId + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int activate(CommandSourceStack source, int slot) {
        try {
            var result = AbilityRuntime.activate(
                    source.getPlayerOrException(), slot, commandKey("activate"));
            return report(source, result.accepted(), result.message(),
                    "Activated slot " + (slot + 1) + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static void reportAction(
            CommandSourceStack source,
            com.envisione.progressiveskills.common.ability.AbilityAction action
    ) {
        if (action instanceof AbilityMessageAction message) {
            success(source, "Action " + action.id() + ". Type message. Text "
                    + message.message().fallback() + ".");
        } else if (action instanceof AbilityHealAction heal) {
            success(source, "Action " + action.id() + ". Type heal. Fixed point amount "
                    + heal.amountUnits() + ".");
        } else {
            AbilityVanillaEffectAction effect = (AbilityVanillaEffectAction) action;
            success(source, "Action " + action.id() + ". Type vanilla effect. Effect "
                    + effect.effect() + ". Amplifier " + effect.amplifier() + ". Duration ticks "
                    + effect.durationTicks() + ".");
        }
    }

    private static int report(
            CommandSourceStack source,
            boolean accepted,
            String detail,
            String successMessage
    ) {
        if (!accepted) {
            failure(source, detail);
            return 0;
        }
        success(source, successMessage + " " + detail + ".");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestAbilities(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                AbilityRuntime.catalog().stream().flatMap(catalog -> catalog.abilities().keySet().stream())
                        .map(ResourceLocation::toString),
                builder
        );
    }

    private static ResourceLocation abilityId(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "ability");
    }

    private static int slot(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "slot") - 1;
    }

    private static IdempotencyKey commandKey(String action) {
        return new IdempotencyKey("phase12/command/" + action + "/" + UUID.randomUUID());
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
