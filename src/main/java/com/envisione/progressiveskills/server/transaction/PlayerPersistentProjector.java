package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.ability.AbilityEntitlementTypes;
import com.envisione.progressiveskills.common.classdef.ClassEntitlementTypes;
import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import com.envisione.progressiveskills.common.transaction.AttributeProjectionSafety;
import com.envisione.progressiveskills.common.skill.AttributeOperation;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Projects the Phase 4 source-resolved health fixture through one owned transient modifier. */
public final class PlayerPersistentProjector implements PersistentProjector {
    private static final Map<UUID, Set<ModifierBinding>> KNOWN_MODIFIERS = new ConcurrentHashMap<>();
    private final MinecraftServer server;

    public PlayerPersistentProjector(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
        Optional<String> unsupported = validateProjectionTypes(changes);
        if (unsupported.isPresent()) {
            return unsupported;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return Optional.of("Target player is not online");
        }
        for (ProjectionChange change : changes) {
            if (isLogical(change.key().targetType())) {
                continue;
            }
            if (change.key().equals(Phase4LifecycleDemo.MAX_HEALTH)) {
                if (change.after().isPresent()
                        && (change.after().getAsLong() < -1_024 || change.after().getAsLong() > 1_024)) {
                    return Optional.of("Health projection exceeds the Phase 4 safety bound");
                }
                continue;
            }
            Optional<AttributeOperation> operation = operation(change);
            if (operation.isEmpty()) {
                return Optional.of("No physical projector is registered for " + change.key());
            }
            if (change.after().isPresent()) {
                long value = change.after().getAsLong();
                if (!AttributeProjectionSafety.isWithinBounds(operation.orElseThrow(), value)) {
                    return Optional.of("Attribute projection exceeds its safety bound");
                }
            }
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(change.key().targetId());
            if (holder.isEmpty() || player.getAttribute(holder.orElseThrow()) == null) {
                return Optional.of("Player has no projected attribute " + change.key().targetId());
            }
        }
        return Optional.empty();
    }

    static Optional<String> validateProjectionTypes(List<ProjectionChange> changes) {
        Objects.requireNonNull(changes, "changes");
        for (ProjectionChange change : changes) {
            Objects.requireNonNull(change, "projection change");
            if (isLogical(change.key().targetType())
                    || change.key().equals(Phase4LifecycleDemo.MAX_HEALTH)
                    || operation(change).isPresent()) {
                continue;
            }
            return Optional.of("No physical projector is registered for " + change.key());
        }
        return Optional.empty();
    }

    @Override
    public void apply(UUID targetId, List<ProjectionChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        ServerPlayer player = Objects.requireNonNull(
                server.getPlayerList().getPlayer(targetId),
                "Target player disconnected after projection validation"
        );
        for (ProjectionChange change : changes) {
            if (isLogical(change.key().targetType())) {
                continue;
            }
            if (change.key().equals(Phase4LifecycleDemo.MAX_HEALTH)) {
                var attribute = Objects.requireNonNull(
                        player.getAttribute(Attributes.MAX_HEALTH),
                        "Player has no max health attribute"
                );
                attribute.removeModifier(Phase4LifecycleDemo.HEALTH_MODIFIER_ID);
                if (change.after().isPresent() && change.after().getAsLong() != 0) {
                    attribute.addOrUpdateTransientModifier(new AttributeModifier(
                            Phase4LifecycleDemo.HEALTH_MODIFIER_ID,
                            change.after().getAsLong(),
                            AttributeModifier.Operation.ADD_VALUE
                    ));
                }
                continue;
            }
            AttributeOperation operation = operation(change).orElseThrow();
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(change.key().targetId()).orElseThrow();
            var attribute = Objects.requireNonNull(
                    player.getAttribute(holder),
                    "Player has no projected attribute"
            );
            var binding = new ModifierBinding(change.key().targetId(), modifierId(change));
            attribute.removeModifier(binding.modifierId());
            if (change.after().isPresent() && change.after().getAsLong() != 0) {
                attribute.addOrUpdateTransientModifier(new AttributeModifier(
                        binding.modifierId(),
                        FixedPoint.toDecimal(change.after().getAsLong()).doubleValue(),
                        vanillaOperation(operation)
                ));
                KNOWN_MODIFIERS.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(binding);
            } else {
                Set<ModifierBinding> known = KNOWN_MODIFIERS.get(player.getUUID());
                if (known != null) {
                    known.remove(binding);
                }
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void clearKnownModifier(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(Phase4LifecycleDemo.HEALTH_MODIFIER_ID);
        }
        Set<ModifierBinding> bindings = KNOWN_MODIFIERS.remove(player.getUUID());
        if (bindings != null) {
            for (ModifierBinding binding : bindings) {
                BuiltInRegistries.ATTRIBUTE.getHolder(binding.attribute()).ifPresent(holder -> {
                    var instance = player.getAttribute(holder);
                    if (instance != null) {
                        instance.removeModifier(binding.modifierId());
                    }
                });
            }
        }
    }

    private static Optional<AttributeOperation> operation(ProjectionChange change) {
        return java.util.Arrays.stream(AttributeOperation.values())
                .filter(candidate -> candidate.targetType().equals(change.key().targetType()))
                .findFirst();
    }

    private static boolean isLogical(net.minecraft.resources.ResourceLocation targetType) {
        return ClassEntitlementTypes.isLogical(targetType)
                || AbilityEntitlementTypes.isLogical(targetType);
    }

    private static AttributeModifier.Operation vanillaOperation(AttributeOperation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
    }

    private static net.minecraft.resources.ResourceLocation modifierId(ProjectionChange change) {
        AttributeOperation operation = operation(change).orElseThrow();
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "progressiveskills",
                "skill_attribute/" + operation.serializedName() + "/"
                        + change.key().targetId().getNamespace() + "/" + change.key().targetId().getPath()
        );
    }

    private record ModifierBinding(
            net.minecraft.resources.ResourceLocation attribute,
            net.minecraft.resources.ResourceLocation modifierId
    ) {
    }
}
