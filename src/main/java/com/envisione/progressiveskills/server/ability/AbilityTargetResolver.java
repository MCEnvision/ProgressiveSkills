package com.envisione.progressiveskills.server.ability;

import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import com.envisione.progressiveskills.common.ability.AbilityTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class AbilityTargetResolver {
    private AbilityTargetResolver() {
    }

    public static Resolution resolve(ServerPlayer player, AbilityTargeting targeting) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(targeting, "targeting");
        if (targeting.mode() == AbilityTargetMode.SELF) {
            return Resolution.accepted(AbilityExecutionTarget.self());
        }
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize().scale(targeting.range());
        Vec3 end = start.add(direction);
        if (targeting.mode() == AbilityTargetMode.BLOCK) {
            BlockHitResult hit = player.serverLevel().clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return Resolution.rejected("No valid block is targeted within the configured range");
            }
            return Resolution.accepted(AbilityExecutionTarget.block(hit.getBlockPos()));
        }
        AABB search = player.getBoundingBox().expandTowards(direction).inflate(1.0D);
        LivingEntity closest = null;
        double closestDistance = targeting.range() * (double) targeting.range();
        for (var entity : player.serverLevel().getEntities(
                player,
                search,
                candidate -> candidate instanceof LivingEntity living
                        && living.isAlive()
                        && !living.isSpectator()
                        && (!targeting.lineOfSight() || player.hasLineOfSight(living))
        )) {
            AABB bounds = entity.getBoundingBox().inflate(0.3D);
            Optional<Vec3> intercept = bounds.clip(start, end);
            if (bounds.contains(start)) {
                intercept = Optional.of(start);
            }
            if (intercept.isEmpty()) {
                continue;
            }
            double distance = start.distanceToSqr(intercept.orElseThrow());
            if (distance <= closestDistance) {
                closest = (LivingEntity) entity;
                closestDistance = distance;
            }
        }
        if (closest == null) {
            return Resolution.rejected("No valid living entity is targeted within the configured range");
        }
        return Resolution.accepted(AbilityExecutionTarget.entity(closest.getUUID()));
    }

    public record Resolution(Optional<AbilityExecutionTarget> target, String message) {
        public Resolution {
            target = Objects.requireNonNull(target, "target");
            message = Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("Ability target resolution message must not be blank");
            }
        }

        public static Resolution accepted(AbilityExecutionTarget target) {
            return new Resolution(Optional.of(target), "Ability target resolved");
        }

        public static Resolution rejected(String message) {
            return new Resolution(Optional.empty(), message);
        }

        public boolean accepted() {
            return target.isPresent();
        }
    }
}
