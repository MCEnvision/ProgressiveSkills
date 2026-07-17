package com.envisione.progressiveskills.common.skill;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class SkillCatalog {
    private final Map<ResourceLocation, SkillDefinition> skills;
    private final Map<ResourceLocation, CurrencyDefinition> currencies;
    private final Map<ResourceLocation, CustomXpRoute> customXpRoutes;

    private SkillCatalog(
            Map<ResourceLocation, SkillDefinition> skills,
            Map<ResourceLocation, CurrencyDefinition> currencies
    ) {
        this.skills = immutable(skills);
        this.currencies = immutable(currencies);
        var routes = new TreeMap<ResourceLocation, CustomXpRoute>(ResourceLocation::compareNamespaced);
        for (SkillDefinition skill : this.skills.values()) {
            for (SkillDefinition.CustomXpSource source : skill.xpSources()) {
                CustomXpRoute previous = routes.putIfAbsent(source.key(), new CustomXpRoute(skill, source));
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate custom XP key " + source.key());
                }
            }
        }
        this.customXpRoutes = immutable(routes);
    }

    public static SkillCatalog from(CanonicalIr ir) {
        Objects.requireNonNull(ir, "ir");
        var currencies = new TreeMap<ResourceLocation, CurrencyDefinition>(ResourceLocation::compareNamespaced);
        ir.definitions().forEach((key, canonical) -> {
            if (key.kind().equals(DefinitionKinds.CURRENCY)) {
                currencies.put(key.id(), SkillCanonicalCodec.decodeCurrency(canonical));
            }
        });
        var skills = new TreeMap<ResourceLocation, SkillDefinition>(ResourceLocation::compareNamespaced);
        ir.definitions().forEach((key, canonical) -> {
            if (key.kind().equals(DefinitionKinds.SKILL)) {
                SkillDefinition skill = SkillCanonicalCodec.decodeSkill(canonical);
                for (SkillDefinition.CurrencyAward award : skill.currencyAwards()) {
                    if (!currencies.containsKey(award.currency())) {
                        throw new IllegalArgumentException("Skill " + skill.id()
                                + " references missing currency " + award.currency());
                    }
                }
                skills.put(key.id(), skill);
            }
        });
        return new SkillCatalog(skills, currencies);
    }

    private static <T> Map<ResourceLocation, T> immutable(Map<ResourceLocation, T> values) {
        var sorted = new TreeMap<ResourceLocation, T>(ResourceLocation::compareNamespaced);
        sorted.putAll(values);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public Map<ResourceLocation, SkillDefinition> skills() {
        return skills;
    }

    public Map<ResourceLocation, CurrencyDefinition> currencies() {
        return currencies;
    }

    public Optional<SkillDefinition> skill(ResourceLocation id) {
        return Optional.ofNullable(skills.get(id));
    }

    public Optional<CurrencyDefinition> currency(ResourceLocation id) {
        return Optional.ofNullable(currencies.get(id));
    }

    public Optional<CustomXpRoute> customXpRoute(ResourceLocation key) {
        return Optional.ofNullable(customXpRoutes.get(key));
    }

    public Map<ResourceLocation, CustomXpRoute> customXpRoutes() {
        return customXpRoutes;
    }

    public record CustomXpRoute(SkillDefinition skill, SkillDefinition.CustomXpSource source) {
        public CustomXpRoute {
            Objects.requireNonNull(skill, "skill");
            Objects.requireNonNull(source, "source");
        }
    }
}
