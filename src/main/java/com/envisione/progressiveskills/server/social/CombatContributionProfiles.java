package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.common.creator.CreatorDefinition;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.social.CombatContributionPolicy;
import com.envisione.progressiveskills.server.creator.CreatorRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public final class CombatContributionProfiles {
    private CombatContributionProfiles() {
    }

    public static Optional<ActiveProfile> active() {
        return CreatorRuntime.catalog().flatMap(catalog -> {
            List<CreatorDefinition> active = catalog.kind(DefinitionKinds.PROFILE).stream()
                    .filter(value -> value.bool("active", false)).toList();
            if (active.size() > 1) {
                throw new IllegalArgumentException("Multiple active progression profiles are configured");
            }
            return active.stream().findFirst().flatMap(CombatContributionProfiles::profile);
        });
    }

    private static Optional<ActiveProfile> profile(CreatorDefinition value) {
        Optional<ResourceLocation> skill = value.id("assist_skill");
        long xpUnitsPerDamage = value.integer("assist_xp_units_per_damage").orElse(0L);
        if (skill.isEmpty() || xpUnitsPerDamage == 0) {
            return Optional.empty();
        }
        return Optional.of(new ActiveProfile(skill.orElseThrow(), new CombatContributionPolicy(
                xpUnitsPerDamage,
                value.integer("assist_max_award_units").orElse(10_000L),
                Math.toIntExact(value.integer("assist_window_ticks").orElse(200L)),
                value.integer("assist_min_damage_milli").orElse(1_000L),
                value.bool("pvp_awards_enabled", true),
                Math.toIntExact(value.integer("pvp_pair_cooldown_ticks").orElse(1_200L)),
                value.integer("pvp_pair_daily_cap_units").orElse(25_000L),
                Math.toIntExact(value.integer("pvp_repeat_multiplier_basis_points").orElse(2_500L)),
                Math.toIntExact(value.integer("pvp_free_level_gap").orElse(5L)),
                Math.toIntExact(value.integer("pvp_level_penalty_basis_points").orElse(500L)),
                Math.toIntExact(value.integer("pvp_minimum_multiplier_basis_points").orElse(1_000L))
        )));
    }

    public record ActiveProfile(ResourceLocation skill, CombatContributionPolicy policy) {
        public ActiveProfile {
            java.util.Objects.requireNonNull(skill, "skill");
            java.util.Objects.requireNonNull(policy, "policy");
        }
    }
}
