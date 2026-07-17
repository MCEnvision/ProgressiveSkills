package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.classdef.ClassStarterKitAction;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.transaction.ActionExecution;
import com.envisione.progressiveskills.common.transaction.TransactionId;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionActionExecutor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
        for (TransitionAction action : actions) {
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
