package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.social.ContributionReceipt;
import com.envisione.progressiveskills.common.social.SharedAwardAllocator;
import com.envisione.progressiveskills.common.skill.SkillDefinition;
import com.envisione.progressiveskills.server.skill.SkillRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PartyContributionService {
    private PartyContributionService() {
    }

    public static ContributionReceipt award(
            ServerPlayer actor,
            ResourceLocation source,
            SkillDefinition skill,
            long totalUnits,
            Map<UUID, Long> weights,
            String explanation
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(weights, "weights");
        if (totalUnits < 1) {
            throw new IllegalArgumentException("Contribution total must be positive");
        }
        MultiplayerSavedData data = MultiplayerSavedData.get(actor.getServer());
        var party = SocialProviderRuntime.parties(actor.getServer()).party(actor.getUUID()).orElseThrow(
                () -> new IllegalStateException("Contribution actor is not in a party"));
        if (!party.members().containsAll(weights.keySet())) {
            throw new IllegalArgumentException("Contribution contains a nonparty participant");
        }
        var targets = new LinkedHashMap<UUID, ServerPlayer>();
        weights.keySet().forEach(member -> {
            ServerPlayer target = actor.getServer().getPlayerList().getPlayer(member);
            if (target == null) {
                throw new IllegalStateException("Every contribution recipient must be online");
            }
            targets.put(member, target);
        });
        Map<UUID, Long> baseShares = SharedAwardAllocator.allocate(totalUnits, weights);
        var adjustedShares = new LinkedHashMap<UUID, Long>();
        baseShares.forEach((member, amount) -> {
            if (amount > 0) {
                adjustedShares.put(member, MentorCatchupService.adjustedAward(
                        data, targets.get(member), skill, amount));
            }
        });
        return awardExact(actor, source, skill, adjustedShares, explanation);
    }

    public static ContributionReceipt awardExact(
            ServerPlayer actor,
            ResourceLocation source,
            SkillDefinition skill,
            Map<UUID, Long> shares,
            String explanation
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(shares, "shares");
        if (shares.isEmpty() || shares.values().stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("Contribution shares must be positive");
        }
        MultiplayerSavedData data = MultiplayerSavedData.get(actor.getServer());
        var party = SocialProviderRuntime.parties(actor.getServer()).party(actor.getUUID()).orElseThrow(
                () -> new IllegalStateException("Contribution actor is not in a party"));
        if (!party.members().containsAll(shares.keySet())) {
            throw new IllegalArgumentException("Contribution contains a nonparty participant");
        }
        var targets = new LinkedHashMap<UUID, ServerPlayer>();
        shares.keySet().forEach(member -> {
            ServerPlayer target = actor.getServer().getPlayerList().getPlayer(member);
            if (target == null) {
                throw new IllegalStateException("Every contribution recipient must be online");
            }
            targets.put(member, target);
        });
        ContributionReceipt receipt = data.shareExactAuthorized(
                actor.getUUID(), party.stableId(), party.members(), source, shares, explanation);
        for (var share : data.pendingShares(actor.getUUID(), receipt.receiptId()).entrySet()) {
            var result = SkillRuntime.awardShared(
                    actor.getUUID(), targets.get(share.getKey()), skill,
                    share.getValue(), receipt.receiptId());
            if (!result.transaction().status().committed()) {
                throw new IllegalStateException("Shared XP award failed. "
                        + result.transaction().message());
            }
            data.markContributionDelivered(
                    actor.getUUID(), receipt.receiptId(), share.getKey());
        }
        data.completeContribution(actor.getUUID(), receipt.receiptId());
        return receipt;
    }
}
