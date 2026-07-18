package com.envisione.progressiveskills.common.carrier;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class CarrierCatalog {
    public static final int MAX_CARRIERS = 256;
    public static final int MAX_TOTAL_ACTIONS = 1_024;
    public static final int MAX_TOTAL_BEHAVIOR_BYTES = 786_432;

    private final Map<ResourceLocation, CarrierDefinition> carriers;
    private final Map<ResourceLocation, CarrierBehaviorSnapshot> behaviors;
    private final Map<ResourceLocation, String> behaviorDigests;

    private CarrierCatalog(Map<ResourceLocation, CarrierDefinition> carriers) {
        this.carriers = immutable(carriers);
        var behaviorValues = new TreeMap<ResourceLocation, CarrierBehaviorSnapshot>(
                ResourceLocation::compareNamespaced
        );
        var digestValues = new TreeMap<ResourceLocation, String>(ResourceLocation::compareNamespaced);
        this.carriers.forEach((id, definition) -> {
            CarrierBehaviorSnapshot snapshot = definition.behaviorSnapshot();
            behaviorValues.put(id, snapshot);
            digestValues.put(id, snapshot.digest());
        });
        this.behaviors = immutable(behaviorValues);
        this.behaviorDigests = immutable(digestValues);
    }

    public static CarrierCatalog from(CanonicalIr ir, SkillCatalog skills, TreeCatalog trees) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(trees, "trees");
        var carriers = new TreeMap<ResourceLocation, CarrierDefinition>(ResourceLocation::compareNamespaced);
        ir.definitions().forEach((key, canonical) -> {
            if (!key.kind().equals(DefinitionKinds.ITEM)) {
                return;
            }
            if (carriers.size() >= MAX_CARRIERS) {
                throw new IllegalArgumentException("Carrier count exceeds " + MAX_CARRIERS);
            }
            CarrierDefinition definition = CarrierCanonicalCodec.decode(canonical);
            if (carriers.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate carrier id " + definition.id());
            }
        });
        validate(carriers, skills, trees);
        return new CarrierCatalog(carriers);
    }

    private static void validate(
            Map<ResourceLocation, CarrierDefinition> carriers,
            SkillCatalog skills,
            TreeCatalog trees
    ) {
        var actionIds = new HashSet<ResourceLocation>();
        int actionCount = 0;
        int behaviorBytes = 0;
        for (CarrierDefinition carrier : carriers.values()) {
            actionCount = Math.addExact(actionCount, carrier.useActions().size());
            behaviorBytes = Math.addExact(
                    behaviorBytes,
                    CarrierBehaviorCodec.encode(carrier.behaviorSnapshot()).length
            );
            for (CarrierUseAction action : carrier.useActions()) {
                if (!actionIds.add(action.id())) {
                    throw new IllegalArgumentException("Carrier use action id must be globally unique " + action.id());
                }
                if (action instanceof CarrierSkillXpAction xp) {
                    requireSkill(carrier, xp.skill(), skills);
                } else if (action instanceof CarrierSkillLevelAction level) {
                    var skill = requireSkill(carrier, level.skill(), skills);
                    int span = skill.curve().maxLevel() - skill.curve().minLevel();
                    if (level.levels() > span) {
                        throw new IllegalArgumentException("Carrier " + carrier.id()
                                + " level action exceeds skill level span " + level.skill());
                    }
                } else if (action instanceof CarrierCurrencyAction currency) {
                    var definition = skills.currency(currency.currency()).orElseThrow(() ->
                            new IllegalArgumentException("Carrier " + carrier.id()
                                    + " references missing currency " + currency.currency()));
                    if (currency.amount() > definition.maximum()) {
                        throw new IllegalArgumentException("Carrier " + carrier.id()
                                + " currency action exceeds its maximum " + currency.currency());
                    }
                } else if (action instanceof CarrierTreeRespecAction tree) {
                    var definition = trees.tree(tree.tree()).orElseThrow(() ->
                            new IllegalArgumentException("Carrier " + carrier.id()
                                    + " references missing tree " + tree.tree()));
                    if (!definition.enabled()) {
                        throw new IllegalArgumentException("Carrier " + carrier.id()
                                + " references disabled tree " + tree.tree());
                    }
                }
            }
        }
        if (actionCount > MAX_TOTAL_ACTIONS) {
            throw new IllegalArgumentException("Carrier action count exceeds " + MAX_TOTAL_ACTIONS);
        }
        if (behaviorBytes > MAX_TOTAL_BEHAVIOR_BYTES) {
            throw new IllegalArgumentException("Carrier behavior bytes exceed " + MAX_TOTAL_BEHAVIOR_BYTES);
        }
    }

    private static com.envisione.progressiveskills.common.skill.SkillDefinition requireSkill(
            CarrierDefinition carrier,
            ResourceLocation skillId,
            SkillCatalog skills
    ) {
        var skill = skills.skill(skillId).orElseThrow(() -> new IllegalArgumentException(
                "Carrier " + carrier.id() + " references missing skill " + skillId
        ));
        if (!skill.enabled()) {
            throw new IllegalArgumentException("Carrier " + carrier.id()
                    + " references disabled skill " + skillId);
        }
        return skill;
    }

    private static <T> Map<ResourceLocation, T> immutable(Map<ResourceLocation, T> values) {
        var sorted = new TreeMap<ResourceLocation, T>(ResourceLocation::compareNamespaced);
        sorted.putAll(values);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public Map<ResourceLocation, CarrierDefinition> carriers() {
        return carriers;
    }

    public Optional<CarrierDefinition> carrier(ResourceLocation id) {
        return Optional.ofNullable(carriers.get(id));
    }

    public Map<ResourceLocation, CarrierBehaviorSnapshot> behaviors() {
        return behaviors;
    }

    public Optional<CarrierBehaviorSnapshot> behavior(ResourceLocation id) {
        return Optional.ofNullable(behaviors.get(id));
    }

    public Map<ResourceLocation, String> behaviorDigests() {
        return behaviorDigests;
    }

    public Optional<String> behaviorDigest(ResourceLocation id) {
        return Optional.ofNullable(behaviorDigests.get(id));
    }
}
