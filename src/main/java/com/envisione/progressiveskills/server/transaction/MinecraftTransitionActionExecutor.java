package com.envisione.progressiveskills.server.transaction;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Minimal typed item-delivery adapter used only by the Phase 4 lifecycle checkpoint. */
public final class MinecraftTransitionActionExecutor implements TransitionActionExecutor {
    private final MinecraftServer server;

    public MinecraftTransitionActionExecutor(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<String> validate(UUID targetId, TransitionAction action) {
        if (!action.type().equals(Phase4LifecycleDemo.ITEM_ACTION_TYPE)) {
            return Optional.of("Unsupported Phase 4 transition action type: " + action.type());
        }
        var player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return Optional.of("Target player is not online");
        }
        ResourceLocation itemId;
        try {
            itemId = StableId.parse(action.payload());
        } catch (IllegalArgumentException exception) {
            return Optional.of(exception.getMessage());
        }
        var item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            return Optional.of("Unknown item id: " + itemId);
        }
        if (action.amount() > item.orElseThrow().getDefaultMaxStackSize() || action.amount() > Integer.MAX_VALUE) {
            return Optional.of("Item amount exceeds one bounded stack");
        }
        var stack = new ItemStack(item.orElseThrow(), (int) action.amount());
        long capacity = 0;
        for (ItemStack existing : player.getInventory().items) {
            if (existing.isEmpty()) {
                capacity += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (capacity >= action.amount()) {
                break;
            }
        }
        if (capacity < action.amount()) {
            return Optional.of("Inventory has no room; Phase 4 refuses delivery before commit");
        }
        return Optional.empty();
    }

    @Override
    public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
        var player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return ActionExecution.failure("Target player disconnected before item delivery");
        }
        ResourceLocation itemId = StableId.parse(action.payload());
        var item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            return ActionExecution.failure("Item disappeared from the registry before delivery: " + itemId);
        }
        var stack = new ItemStack(item.orElseThrow(), Math.toIntExact(action.amount()));
        var before = new ArrayList<ItemStack>(player.getInventory().items.size());
        player.getInventory().items.forEach(existing -> before.add(existing.copy()));
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            for (int index = 0; index < before.size(); index++) {
                player.getInventory().items.set(index, before.get(index));
            }
            player.getInventory().setChanged();
            return ActionExecution.failure("Inventory changed after validation; item was not delivered");
        }
        return ActionExecution.success("Delivered " + action.amount() + "x " + itemId
                + " for transaction " + transactionId);
    }
}
