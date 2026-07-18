package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.creator.CreatorDefinition;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.common.skill.SkillProgress;
import com.envisione.progressiveskills.server.creator.CreatorRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public final class MentorCatchupService {
    private MentorCatchupService() {
    }

    public static long adjustedAward(
            MultiplayerSavedData data,
            ServerPlayer mentee,
            SkillDefinition skill,
            long baseAmount
    ) {
        if (baseAmount < 0) {
            throw new IllegalArgumentException("Mentor base award must not be negative");
        }
        Optional<java.util.UUID> mentorId = data.mentor(mentee.getUUID());
        if (mentorId.isEmpty()) {
            return baseAmount;
        }
        ServerPlayer mentor = mentee.getServer().getPlayerList().getPlayer(mentorId.orElseThrow());
        if (mentor == null) {
            return baseAmount;
        }
        var transactions = TransactionRuntime.context(mentee.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        int menteeLevel = SkillProgress.from(
                skill, transactions.service().snapshot(mentee.getUUID())).level();
        int mentorLevel = SkillProgress.from(
                skill, transactions.service().snapshot(mentor.getUUID())).level();
        if (mentorLevel <= menteeLevel) {
            return baseAmount;
        }
        CreatorDefinition profile = profile().orElse(null);
        long basisPoints = profile == null ? 2500L
                : profile.integer("mentor_bonus_basis_points").orElse(2500L);
        long maximumBonus = profile == null ? Long.MAX_VALUE
                : profile.integer("mentor_max_bonus_per_award").orElse(Long.MAX_VALUE);
        long maximumGap = profile == null ? Integer.MAX_VALUE
                : profile.integer("mentor_max_level_gap").orElse(Integer.MAX_VALUE);
        if (basisPoints < 0 || basisPoints > 100_000 || maximumBonus < 0 || maximumGap < 1) {
            throw new IllegalArgumentException("Mentor catchup profile is invalid");
        }
        long gap = Math.min(maximumGap, Math.subtractExact(mentorLevel, menteeLevel));
        long scaledBasisPoints = Math.min(100_000L, Math.multiplyExact(basisPoints, gap));
        long bonus = BigInteger.valueOf(baseAmount).multiply(BigInteger.valueOf(scaledBasisPoints))
                .divide(BigInteger.valueOf(10_000L)).min(BigInteger.valueOf(maximumBonus)).longValueExact();
        return Math.addExact(baseAmount, bonus);
    }

    private static Optional<CreatorDefinition> profile() {
        return CreatorRuntime.catalog().flatMap(catalog -> {
            List<CreatorDefinition> active = catalog.kind(DefinitionKinds.PROFILE).stream()
                    .filter(value -> value.bool("active", false)).toList();
            if (active.size() > 1) {
                throw new IllegalArgumentException("Multiple active progression profiles are configured");
            }
            return active.stream().findFirst();
        });
    }
}
