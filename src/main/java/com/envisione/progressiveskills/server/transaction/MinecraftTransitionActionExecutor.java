package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.ability.AbilityAction;
import com.envisione.progressiveskills.common.ability.AbilityCatalog;
import com.envisione.progressiveskills.common.ability.AbilityCost;
import com.envisione.progressiveskills.common.ability.AbilityCostType;
import com.envisione.progressiveskills.common.ability.AbilityDefinition;
import com.envisione.progressiveskills.common.ability.AbilityHealAction;
import com.envisione.progressiveskills.common.ability.AbilityMessageAction;
import com.envisione.progressiveskills.common.ability.AbilityVanillaCost;
import com.envisione.progressiveskills.common.ability.AbilityVanillaEffectAction;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassStarterKitAction;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import com.envisione.progressiveskills.server.ability.AbilityExecutionTarget;
import com.envisione.progressiveskills.server.ability.AbilityTransitionActions;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Executes bounded Minecraft transition actions. */
public final class MinecraftTransitionActionExecutor implements TransitionActionExecutor {
    private static final long MAX_ITEM_DELIVERY = 36L * 64L;
    private final MinecraftServer server;

    public MinecraftTransitionActionExecutor(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<String> validate(UUID targetId, TransitionAction action) {
        return validateAll(targetId, List.of(action));
    }

    @Override
    public Optional<String> validateAll(UUID targetId, List<TransitionAction> actions) {
        Objects.requireNonNull(actions, "actions");
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        var player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return Optional.of("Target player is not online");
        }
        var simulated = new ArrayList<ItemStack>(player.getInventory().items.size());
        player.getInventory().items.forEach(existing -> simulated.add(existing.copy()));
        int hunger = player.getFoodData().getFoodLevel();
        long experience = player.totalExperience;
        AbilityCatalog abilities = null;
        for (TransitionAction action : actions) {
            if (AbilityTransitionActions.isAbilityAction(action.type())) {
                if (abilities == null) {
                    try {
                        abilities = abilityCatalog();
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        return Optional.of(exception.getMessage());
                    }
                }
                Optional<String> rejection = validateAbilityAction(player, abilities, action);
                if (rejection.isPresent()) {
                    return rejection;
                }
                if (action.type().equals(AbilityTransitionActions.HUNGER_COST)) {
                    if (action.amount() > hunger) {
                        return Optional.of("Player does not have enough hunger for the ability");
                    }
                    hunger = Math.subtractExact(hunger, Math.toIntExact(action.amount()));
                } else if (action.type().equals(AbilityTransitionActions.EXPERIENCE_COST)) {
                    if (action.amount() > experience) {
                        return Optional.of("Player does not have enough experience for the ability");
                    }
                    experience = Math.subtractExact(experience, action.amount());
                }
                continue;
            }
            Optional<String> rejection = validateAndReserve(simulated, action);
            if (rejection.isPresent()) {
                return rejection;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateAndReserve(
            List<ItemStack> inventory,
            TransitionAction action
    ) {
        List<ItemDelivery> deliveries;
        try {
            deliveries = deliveries(action);
        } catch (IllegalArgumentException exception) {
            return Optional.of(exception.getMessage());
        }
        for (ItemDelivery delivery : deliveries) {
            Optional<String> rejection = reserve(inventory, delivery);
            if (rejection.isPresent()) {
                return rejection;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> reserve(
            List<ItemStack> inventory,
            ItemDelivery delivery
    ) {
        var item = BuiltInRegistries.ITEM.getOptional(delivery.item());
        if (item.isEmpty()) {
            return Optional.of("Unknown item id: " + delivery.item());
        }
        if (delivery.count() > MAX_ITEM_DELIVERY) {
            return Optional.of("Item amount exceeds the bounded inventory delivery limit");
        }
        var stack = new ItemStack(item.orElseThrow(), delivery.count());
        int remaining = stack.getCount();
        for (ItemStack existing : inventory) {
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int moved = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining -= moved;
                }
                if (remaining == 0) {
                    return Optional.empty();
                }
            }
        }
        for (int index = 0; index < inventory.size() && remaining > 0; index++) {
            if (inventory.get(index).isEmpty()) {
                int moved = Math.min(remaining, stack.getMaxStackSize());
                inventory.set(index, new ItemStack(item.orElseThrow(), moved));
                remaining -= moved;
            }
        }
        if (remaining > 0) {
            return Optional.of("Inventory has no room for the complete transition delivery");
        }
        return Optional.empty();
    }

    @Override
    public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
        var player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return ActionExecution.failure("Target player disconnected before item delivery");
        }
        if (AbilityTransitionActions.isAbilityAction(action.type())) {
            return executeAbility(player, transactionId, action);
        }
        var before = new ArrayList<ItemStack>(player.getInventory().items.size());
        player.getInventory().items.forEach(existing -> before.add(existing.copy()));
        try {
            for (ItemDelivery delivery : deliveries(action)) {
                var item = BuiltInRegistries.ITEM.getOptional(delivery.item());
                if (item.isEmpty()) {
                    restore(player.getInventory().items, before);
                    player.getInventory().setChanged();
                    return ActionExecution.failure(
                            "Item disappeared from the registry before delivery: " + delivery.item());
                }
                var stack = new ItemStack(item.orElseThrow(), delivery.count());
                if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                    restore(player.getInventory().items, before);
                    player.getInventory().setChanged();
                    return ActionExecution.failure("Inventory changed after validation and no items were delivered");
                }
            }
        } catch (IllegalArgumentException exception) {
            restore(player.getInventory().items, before);
            player.getInventory().setChanged();
            return ActionExecution.failure(exception.getMessage());
        }
        return ActionExecution.success("Delivered " + action.amount() + " items"
                + " for transaction " + transactionId);
    }

    private ActionExecution executeAbility(
            ServerPlayer player,
            TransactionId transactionId,
            TransitionAction transition
    ) {
        AbilityCatalog abilities;
        try {
            abilities = abilityCatalog();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ActionExecution.failure(exception.getMessage());
        }
        Optional<String> rejection = validateAbilityAction(player, abilities, transition);
        if (rejection.isPresent()) {
            return ActionExecution.failure(rejection.orElseThrow());
        }
        try {
            if (transition.type().equals(AbilityTransitionActions.HUNGER_COST)) {
                player.getFoodData().setFoodLevel(Math.subtractExact(
                        player.getFoodData().getFoodLevel(), Math.toIntExact(transition.amount())));
            } else if (transition.type().equals(AbilityTransitionActions.EXPERIENCE_COST)) {
                player.giveExperiencePoints(-Math.toIntExact(transition.amount()));
            } else {
                executeNativeAction(player, abilities, transition);
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return ActionExecution.failure(exception.getMessage());
        }
        return ActionExecution.success("Executed ability transition for transaction " + transactionId);
    }

    private static void executeNativeAction(
            ServerPlayer player,
            AbilityCatalog abilities,
            TransitionAction transition
    ) {
        var reference = AbilityTransitionActions.decodeAction(transition);
        AbilityDefinition ability = abilities.ability(reference.abilityId()).orElseThrow();
        AbilityAction action = findAction(ability, reference.memberId());
        if (action instanceof AbilityMessageAction message) {
            player.sendSystemMessage(message.message().localizationKey()
                    .map(key -> Component.translatableWithFallback(key, message.message().fallback()))
                    .orElseGet(() -> Component.literal(message.message().fallback())));
            return;
        }
        LivingEntity target = livingTarget(player, reference.target()).orElseThrow(
                () -> new IllegalArgumentException("Ability action requires a living target"));
        if (action instanceof AbilityHealAction heal) {
            target.heal(FixedPoint.toDecimal(heal.amountUnits()).floatValue());
            return;
        }
        if (action instanceof AbilityVanillaEffectAction effect) {
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown vanilla effect " + effect.effect()));
            target.addEffect(new MobEffectInstance(
                    holder,
                    effect.durationTicks(),
                    effect.amplifier(),
                    effect.ambient(),
                    effect.showParticles(),
                    effect.showIcon()
            ));
            return;
        }
        throw new IllegalArgumentException("Unsupported Core ability action " + action.type());
    }

    private Optional<String> validateAbilityAction(
            ServerPlayer player,
            AbilityCatalog abilities,
            TransitionAction transition
    ) {
        try {
            if (transition.type().equals(AbilityTransitionActions.HUNGER_COST)
                    || transition.type().equals(AbilityTransitionActions.EXPERIENCE_COST)) {
                var reference = AbilityTransitionActions.decodeCost(transition);
                AbilityDefinition ability = abilities.ability(reference.abilityId()).orElseThrow(
                        () -> new IllegalArgumentException("Unknown ability " + reference.abilityId()));
                AbilityVanillaCost cost = findVanillaCost(ability, reference.memberId());
                ResourceLocation expectedType = cost.type() == AbilityCostType.HUNGER
                        ? AbilityTransitionActions.HUNGER_COST
                        : AbilityTransitionActions.EXPERIENCE_COST;
                if (!expectedType.equals(transition.type()) || cost.amount() != transition.amount()) {
                    return Optional.of("Ability cost transition does not match the live definition");
                }
                requireSource(transition, reference);
                return Optional.empty();
            }
            var reference = AbilityTransitionActions.decodeAction(transition);
            AbilityDefinition ability = abilities.ability(reference.abilityId()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown ability " + reference.abilityId()));
            AbilityAction action = findAction(ability, reference.memberId());
            ResourceLocation expectedType;
            long expectedAmount;
            if (action instanceof AbilityMessageAction) {
                expectedType = AbilityTransitionActions.MESSAGE;
                expectedAmount = 1;
            } else if (action instanceof AbilityHealAction heal) {
                expectedType = AbilityTransitionActions.HEAL;
                expectedAmount = heal.amountUnits();
            } else if (action instanceof AbilityVanillaEffectAction effect) {
                expectedType = AbilityTransitionActions.VANILLA_EFFECT;
                expectedAmount = effect.durationTicks();
                if (BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect()).isEmpty()) {
                    return Optional.of("Unknown vanilla effect " + effect.effect());
                }
            } else {
                return Optional.of("Unsupported Core ability action " + action.type());
            }
            if (!expectedType.equals(transition.type()) || expectedAmount != transition.amount()) {
                return Optional.of("Ability action transition does not match the live definition");
            }
            requireSource(transition, reference);
            Optional<String> targetRejection = validateTarget(player, ability, reference.target());
            if (targetRejection.isPresent()) {
                return targetRejection;
            }
            if (!(action instanceof AbilityMessageAction)
                    && livingTarget(player, reference.target()).isEmpty()) {
                return Optional.of("Ability action requires a living target");
            }
            return Optional.empty();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    private static void requireSource(
            TransitionAction transition,
            AbilityTransitionActions.MemberReference reference
    ) {
        if (!transition.source().ownerKind().equals(
                com.envisione.progressiveskills.common.id.DefinitionKinds.ABILITY.id())
                || !transition.source().ownerId().equals(reference.abilityId())
                || !transition.source().grantId().equals(reference.memberId())) {
            throw new IllegalArgumentException("Ability transition source does not match its payload");
        }
    }

    private static Optional<String> validateTarget(
            ServerPlayer player,
            AbilityDefinition ability,
            AbilityExecutionTarget target
    ) {
        if (ability.targeting().mode() != target.mode()) {
            return Optional.of("Ability target mode does not match the live definition");
        }
        if (target.mode() == com.envisione.progressiveskills.common.ability.AbilityTargetMode.SELF) {
            return Optional.empty();
        }
        double maximumDistance = (double) ability.targeting().range() * ability.targeting().range();
        if (target.mode() == com.envisione.progressiveskills.common.ability.AbilityTargetMode.ENTITY) {
            var entity = player.serverLevel().getEntity(target.entityId().orElseThrow());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                return Optional.of("Ability entity target is no longer valid");
            }
            if (player.getEyePosition().distanceToSqr(living.getBoundingBox().getCenter()) > maximumDistance) {
                return Optional.of("Ability entity target is outside the configured range");
            }
            if (ability.targeting().lineOfSight() && !player.hasLineOfSight(living)) {
                return Optional.of("Ability entity target is outside line of sight");
            }
            return Optional.empty();
        }
        var pos = target.blockPos().orElseThrow();
        if (!player.serverLevel().isInWorldBounds(pos) || player.serverLevel().getBlockState(pos).isAir()) {
            return Optional.of("Ability block target is no longer valid");
        }
        Vec3 center = Vec3.atCenterOf(pos);
        if (player.getEyePosition().distanceToSqr(center) > maximumDistance) {
            return Optional.of("Ability block target is outside the configured range");
        }
        if (ability.targeting().lineOfSight()) {
            var hit = player.serverLevel().clip(new ClipContext(
                    player.getEyePosition(), center, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK
                    || !hit.getBlockPos().equals(pos)) {
                return Optional.of("Ability block target is outside line of sight");
            }
        }
        return Optional.empty();
    }

    private static Optional<LivingEntity> livingTarget(
            ServerPlayer player,
            AbilityExecutionTarget target
    ) {
        return switch (target.mode()) {
            case SELF -> Optional.of(player);
            case ENTITY -> Optional.ofNullable(player.serverLevel().getEntity(target.entityId().orElseThrow()))
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast);
            case BLOCK -> Optional.empty();
        };
    }

    private static AbilityAction findAction(AbilityDefinition ability, ResourceLocation actionId) {
        return ability.actions().stream()
                .filter(action -> action.id().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown action " + actionId + " for ability " + ability.id()));
    }

    private static AbilityVanillaCost findVanillaCost(
            AbilityDefinition ability,
            ResourceLocation costId
    ) {
        AbilityCost cost = ability.costs().stream()
                .filter(candidate -> candidate.id().equals(costId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown cost " + costId + " for ability " + ability.id()));
        if (!(cost instanceof AbilityVanillaCost vanilla)) {
            throw new IllegalArgumentException("Ability cost is not a vanilla player cost " + costId);
        }
        return vanilla;
    }

    private static AbilityCatalog abilityCatalog() {
        var canonical = PackRuntime.service()
                .filter(service -> service.live().generation() > 0)
                .map(service -> service.live().snapshot().canonicalIr())
                .orElseThrow(() -> new IllegalStateException("Ability catalog is unavailable"));
        SkillCatalog skills = SkillCatalog.from(canonical);
        TreeCatalog trees = TreeCatalog.from(canonical, skills);
        ClassCatalog classes = ClassCatalog.from(canonical, skills, trees);
        return AbilityCatalog.from(canonical, skills, classes);
    }

    private static List<ItemDelivery> deliveries(TransitionAction action) {
        if (action.type().equals(Phase4LifecycleDemo.ITEM_ACTION_TYPE)) {
            if (action.amount() > MAX_ITEM_DELIVERY || action.amount() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Item amount exceeds the bounded inventory delivery limit");
            }
            return List.of(new ItemDelivery(
                    StableId.parse(action.payload()), Math.toIntExact(action.amount())));
        }
        if (action.type().equals(ClassStarterKitAction.TYPE)) {
            var counts = ClassStarterKitAction.decode(action.payload());
            if (ClassStarterKitAction.total(counts) != action.amount()) {
                throw new IllegalArgumentException("Starter kit action total does not match its amount");
            }
            return counts.entrySet().stream()
                    .map(entry -> new ItemDelivery(entry.getKey(), entry.getValue()))
                    .toList();
        }
        throw new IllegalArgumentException("Unsupported transition action type: " + action.type());
    }

    private static void restore(List<ItemStack> inventory, List<ItemStack> before) {
        for (int index = 0; index < before.size(); index++) {
            inventory.set(index, before.get(index));
        }
    }

    private record ItemDelivery(ResourceLocation item, int count) {
        private ItemDelivery {
            item = StableId.requireValid(item);
            if (count < 1) {
                throw new IllegalArgumentException("Item delivery count must be positive");
            }
        }
    }
}
