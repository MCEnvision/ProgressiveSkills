package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;

import java.util.Objects;

public record SkillProgress(
        long activeXpUnits,
        long bankedXpUnits,
        int level,
        int highestLevel,
        long intoLevelUnits
) {
    public static SkillProgress from(SkillDefinition skill, ProgressionSnapshot snapshot) {
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(snapshot, "snapshot");
        long active = snapshot.balances().getOrDefault(SkillStateIds.activeXp(skill.id()), 0L);
        long banked = snapshot.balances().getOrDefault(SkillStateIds.bankedXp(skill.id()), 0L);
        if (active < 0 || active > skill.curve().capUnits() || banked < 0) {
            throw new IllegalStateException("Stored skill XP or bank is outside its bounds");
        }
        int derivedLevel = skill.curve().levelForUnits(active);
        long storedLevel = snapshot.balances().getOrDefault(SkillStateIds.level(skill.id()), (long) skill.curve().minLevel());
        long storedHighest = snapshot.balances().getOrDefault(
                SkillStateIds.highestLevel(skill.id()), (long) skill.curve().minLevel()
        );
        if (storedLevel != derivedLevel) {
            throw new IllegalStateException("Stored skill level does not match fixed point XP");
        }
        if (storedHighest < derivedLevel || storedHighest > Integer.MAX_VALUE) {
            throw new IllegalStateException("Stored highest skill level is inconsistent");
        }
        return new SkillProgress(active, banked, derivedLevel, Math.toIntExact(storedHighest),
                skill.curve().intoLevelUnits(active));
    }
}
