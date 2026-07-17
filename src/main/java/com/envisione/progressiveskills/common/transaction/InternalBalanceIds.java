package com.envisione.progressiveskills.common.transaction;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class InternalBalanceIds {
    private static final Set<String> ABILITY_CATEGORIES = Set.of("cooldown", "charges", "recharge");

    private InternalBalanceIds() {
    }

    public static boolean isReserved(ResourceLocation balanceId) {
        return isAbilityState(balanceId);
    }

    public static boolean isAbilityState(ResourceLocation balanceId) {
        ResourceLocation valid = StableId.requireValid(balanceId);
        if (!valid.getNamespace().equals("progressiveskills")) {
            return false;
        }
        String[] segments = valid.getPath().split("/", -1);
        if (segments.length != 3 || !segments[0].equals("ability_state")
                || !ABILITY_CATEGORIES.contains(segments[1]) || segments[2].length() != 64) {
            return false;
        }
        return segments[2].chars().allMatch(character -> character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f');
    }
}
