package com.envisione.progressiveskills.common.requirement;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public record RequirementDependency(Kind kind, ResourceLocation id) implements Comparable<RequirementDependency> {
    public RequirementDependency {
        if (kind == null) {
            throw new IllegalArgumentException("Requirement dependency kind is required");
        }
        id = StableId.requireValid(id);
    }

    @Override
    public int compareTo(RequirementDependency other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : id.compareNamespaced(other.id);
    }

    public String serialized() {
        return kind.serializedName() + ":" + id;
    }

    public enum Kind {
        SKILL_LEVEL,
        CURRENCY;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
