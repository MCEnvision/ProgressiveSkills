package com.envisione.progressiveskills.common.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class SkillStateIds {
    private SkillStateIds() {
    }

    public static ResourceLocation activeXp(ResourceLocation skillId) {
        return state("skill_xp", skillId);
    }

    public static ResourceLocation bankedXp(ResourceLocation skillId) {
        return state("skill_bank", skillId);
    }

    public static ResourceLocation level(ResourceLocation skillId) {
        return state("skill_level", skillId);
    }

    public static ResourceLocation highestLevel(ResourceLocation skillId) {
        return state("skill_highest", skillId);
    }

    private static ResourceLocation state(String prefix, ResourceLocation skillId) {
        Objects.requireNonNull(skillId, "skillId");
        return ResourceLocation.fromNamespaceAndPath(
                "progressiveskills",
                prefix + "/" + skillId.getNamespace() + "/" + skillId.getPath()
        );
    }
}
