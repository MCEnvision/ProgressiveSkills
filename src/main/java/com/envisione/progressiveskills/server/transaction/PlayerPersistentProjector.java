package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.transaction.PersistentProjector;
import com.envisione.progressiveskills.common.transaction.ProjectionChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Projects the Phase 4 source-resolved health fixture through one owned transient modifier. */
public final class PlayerPersistentProjector implements PersistentProjector {
    private final MinecraftServer server;

    public PlayerPersistentProjector(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
        ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return Optional.of("Target player is not online");
        }
        for (ProjectionChange change : changes) {
            if (!change.key().equals(Phase4LifecycleDemo.MAX_HEALTH)) {
                return Optional.of("No Phase 4 physical projector is registered for " + change.key());
            }
            if (change.after().isPresent()
                    && (change.after().getAsLong() < -1_024 || change.after().getAsLong() > 1_024)) {
                return Optional.of("Health projection exceeds the Phase 4 safety bound");
            }
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
        var attribute = Objects.requireNonNull(
                player.getAttribute(Attributes.MAX_HEALTH),
                "Player has no max-health attribute"
        );
        for (ProjectionChange change : changes) {
            if (!change.key().equals(Phase4LifecycleDemo.MAX_HEALTH)) {
                throw new IllegalArgumentException("Unsupported persistent target: " + change.key());
            }
            attribute.removeModifier(Phase4LifecycleDemo.HEALTH_MODIFIER_ID);
            if (change.after().isPresent() && change.after().getAsLong() != 0) {
                attribute.addOrUpdateTransientModifier(new AttributeModifier(
                        Phase4LifecycleDemo.HEALTH_MODIFIER_ID,
                        change.after().getAsLong(),
                        AttributeModifier.Operation.ADD_VALUE
                ));
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
    }
}
