package com.envisione.progressiveskills.server.ability;

import com.envisione.progressiveskills.common.ability.AbilityAction;
import com.envisione.progressiveskills.common.ability.AbilityHealAction;
import com.envisione.progressiveskills.common.ability.AbilityMessageAction;
import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import com.envisione.progressiveskills.common.ability.AbilityVanillaCost;
import com.envisione.progressiveskills.common.ability.AbilityVanillaEffectAction;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public final class AbilityTransitionActions {
    public static final ResourceLocation HUNGER_COST = id("ability_hunger_cost");
    public static final ResourceLocation EXPERIENCE_COST = id("ability_experience_cost");
    public static final ResourceLocation MESSAGE = id("ability_message");
    public static final ResourceLocation HEAL = id("ability_heal");
    public static final ResourceLocation VANILLA_EFFECT = id("ability_vanilla_effect");

    private AbilityTransitionActions() {
    }

    public static TransitionAction cost(ResourceLocation abilityId, AbilityVanillaCost cost) {
        Objects.requireNonNull(cost, "cost");
        ResourceLocation type = switch (cost.type()) {
            case HUNGER -> HUNGER_COST;
            case EXPERIENCE -> EXPERIENCE_COST;
            case CURRENCY -> throw new IllegalArgumentException("Named currency costs are transaction balances");
        };
        ResourceLocation validAbilityId = StableId.requireValid(abilityId);
        return transition(
                type,
                validAbilityId,
                cost.id(),
                validAbilityId + "|" + cost.id(),
                cost.amount()
        );
    }

    public static TransitionAction action(
            ResourceLocation abilityId,
            AbilityAction action,
            AbilityExecutionTarget target
    ) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        ResourceLocation type;
        long amount;
        if (action instanceof AbilityMessageAction) {
            type = MESSAGE;
            amount = 1;
        } else if (action instanceof AbilityHealAction heal) {
            type = HEAL;
            amount = heal.amountUnits();
        } else if (action instanceof AbilityVanillaEffectAction effect) {
            type = VANILLA_EFFECT;
            amount = effect.durationTicks();
        } else {
            throw new IllegalArgumentException("Unsupported Core ability action " + action.type());
        }
        ResourceLocation validAbilityId = StableId.requireValid(abilityId);
        return transition(
                type,
                validAbilityId,
                action.id(),
                validAbilityId + "|" + action.id() + "|" + encodeTarget(target),
                amount
        );
    }

    public static boolean isAbilityAction(ResourceLocation type) {
        return HUNGER_COST.equals(type)
                || EXPERIENCE_COST.equals(type)
                || MESSAGE.equals(type)
                || HEAL.equals(type)
                || VANILLA_EFFECT.equals(type);
    }

    public static MemberReference decodeCost(TransitionAction action) {
        requireType(action, HUNGER_COST, EXPERIENCE_COST);
        String[] fields = action.payload().split("\\|", -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException("Ability cost transition payload is invalid");
        }
        var reference = new MemberReference(
                StableId.parse(fields[0]),
                StableId.parse(fields[1]),
                AbilityExecutionTarget.self()
        );
        if (!encodeCost(reference).equals(action.payload())) {
            throw new IllegalArgumentException("Ability cost transition payload is not canonical");
        }
        return reference;
    }

    public static MemberReference decodeAction(TransitionAction action) {
        requireType(action, MESSAGE, HEAL, VANILLA_EFFECT);
        String[] fields = action.payload().split("\\|", -1);
        if (fields.length < 3 || fields.length > 6) {
            throw new IllegalArgumentException("Ability action transition payload is invalid");
        }
        var reference = new MemberReference(
                StableId.parse(fields[0]),
                StableId.parse(fields[1]),
                decodeTarget(fields, 2)
        );
        if (!encodeAction(reference).equals(action.payload())) {
            throw new IllegalArgumentException("Ability action transition payload is not canonical");
        }
        return reference;
    }

    private static TransitionAction transition(
            ResourceLocation type,
            ResourceLocation abilityId,
            ResourceLocation memberId,
            String payload,
            long amount
    ) {
        return new TransitionAction(
                type,
                new GrantSourceId(DefinitionKinds.ABILITY.id(), abilityId, memberId),
                payload,
                amount,
                RepeatPolicy.ALWAYS,
                DeliveryContract.BEST_EFFORT,
                TransitionFailurePolicy.STOP
        );
    }

    private static String encodeCost(MemberReference reference) {
        return reference.abilityId() + "|" + reference.memberId();
    }

    private static String encodeAction(MemberReference reference) {
        return reference.abilityId() + "|" + reference.memberId() + "|"
                + encodeTarget(reference.target());
    }

    private static String encodeTarget(AbilityExecutionTarget target) {
        return switch (target.mode()) {
            case SELF -> "self";
            case ENTITY -> "entity|" + target.entityId().orElseThrow();
            case BLOCK -> {
                BlockPos pos = target.blockPos().orElseThrow();
                yield "block|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
            }
        };
    }

    private static AbilityExecutionTarget decodeTarget(String[] fields, int offset) {
        AbilityTargetMode mode = AbilityTargetMode.parse(fields[offset]);
        try {
            return switch (mode) {
                case SELF -> {
                    if (fields.length != offset + 1) {
                        throw new IllegalArgumentException("Self ability target payload is invalid");
                    }
                    yield AbilityExecutionTarget.self();
                }
                case ENTITY -> {
                    if (fields.length != offset + 2) {
                        throw new IllegalArgumentException("Entity ability target payload is invalid");
                    }
                    yield AbilityExecutionTarget.entity(UUID.fromString(fields[offset + 1]));
                }
                case BLOCK -> {
                    if (fields.length != offset + 4) {
                        throw new IllegalArgumentException("Block ability target payload is invalid");
                    }
                    yield AbilityExecutionTarget.block(new BlockPos(
                            Integer.parseInt(fields[offset + 1]),
                            Integer.parseInt(fields[offset + 2]),
                            Integer.parseInt(fields[offset + 3])
                    ));
                }
            };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Ability target coordinate is invalid", exception);
        }
    }

    private static void requireType(TransitionAction action, ResourceLocation... accepted) {
        Objects.requireNonNull(action, "action");
        for (ResourceLocation type : accepted) {
            if (type.equals(action.type())) {
                return;
            }
        }
        throw new IllegalArgumentException("Transition action has the wrong ability type");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    public record MemberReference(
            ResourceLocation abilityId,
            ResourceLocation memberId,
            AbilityExecutionTarget target
    ) {
        public MemberReference {
            abilityId = StableId.requireValid(abilityId);
            memberId = StableId.requireValid(memberId);
            Objects.requireNonNull(target, "target");
        }
    }
}
