package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SkillDefinition(
        ResourceLocation id,
        DefinitionPresentation presentation,
        boolean enabled,
        SkillCurve curve,
        List<CurrencyAward> currencyAwards,
        List<CustomXpSource> xpSources,
        List<AttributeGrant> attributeGrants
) {
    public SkillDefinition {
        id = StableId.requireValid(id);
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(curve, "curve");
        currencyAwards = List.copyOf(Objects.requireNonNull(currencyAwards, "currencyAwards"));
        xpSources = List.copyOf(Objects.requireNonNull(xpSources, "xpSources"));
        attributeGrants = List.copyOf(Objects.requireNonNull(attributeGrants, "attributeGrants"));
        requireUniqueIds(currencyAwards.stream().map(CurrencyAward::id).toList(), "currency award");
        requireUniqueIds(xpSources.stream().map(CustomXpSource::id).toList(), "XP source");
        requireUniqueIds(attributeGrants.stream().map(AttributeGrant::id).toList(), "attribute grant");
    }

    private static void requireUniqueIds(List<ResourceLocation> ids, String type) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate " + type + " id");
        }
    }

    public record CurrencyAward(ResourceLocation id, ResourceLocation currency, long amountPerLevel) {
        public CurrencyAward {
            id = StableId.requireValid(id);
            currency = StableId.requireValid(currency);
            if (amountPerLevel <= 0) {
                throw new IllegalArgumentException("Currency amount per level must be positive");
            }
        }
    }

    public record CustomXpSource(
            ResourceLocation id,
            ResourceLocation key,
            long amountUnits,
            String repeatPolicy
    ) {
        public CustomXpSource {
            id = StableId.requireValid(id);
            key = StableId.requireValid(key);
            repeatPolicy = Objects.requireNonNull(repeatPolicy, "repeatPolicy");
            if (amountUnits <= 0) {
                throw new IllegalArgumentException("Custom XP amount must be positive");
            }
            if (!repeatPolicy.equals("always")) {
                throw new IllegalArgumentException("Phase 7 custom XP sources require repeat policy always");
            }
        }
    }

    public record AttributeGrant(
            ResourceLocation id,
            ResourceLocation attribute,
            AttributeOperation operation,
            long valueUnits,
            int fromLevel,
            int toLevel,
            boolean scaling
    ) {
        public AttributeGrant {
            id = StableId.requireValid(id);
            attribute = StableId.requireValid(attribute);
            Objects.requireNonNull(operation, "operation");
            if (valueUnits == 0) {
                throw new IllegalArgumentException("Attribute grant value must not be zero");
            }
            if (fromLevel < 0 || toLevel < fromLevel) {
                throw new IllegalArgumentException("Attribute grant level range is invalid");
            }
            if (!scaling && fromLevel != toLevel) {
                throw new IllegalArgumentException("Discrete attribute grants require one level");
            }
        }

        public long valueAt(int level) {
            if (level < fromLevel) {
                return 0;
            }
            if (!scaling) {
                return valueUnits;
            }
            int count = Math.min(level, toLevel) - fromLevel + 1;
            return Math.multiplyExact(valueUnits, count);
        }
    }
}
