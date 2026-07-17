package com.envisione.progressiveskills.common.ability;

import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassGrant;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.AttributeProjectionSafety;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class AbilityCatalog {
    public static final int MAX_ABILITIES = 64;
    public static final int MAX_TOTAL_PERSISTENT_EFFECTS = 384;
    public static final int MAX_TOTAL_COSTS = 192;
    public static final int MAX_TOTAL_ACTIONS = 256;

    private final Map<ResourceLocation, AbilityDefinition> abilities;

    private AbilityCatalog(Map<ResourceLocation, AbilityDefinition> abilities) {
        var sorted = new TreeMap<ResourceLocation, AbilityDefinition>(ResourceLocation::compareNamespaced);
        sorted.putAll(abilities);
        this.abilities = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static AbilityCatalog from(CanonicalIr ir, SkillCatalog skills, ClassCatalog classes) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(classes, "classes");
        var abilities = new TreeMap<ResourceLocation, AbilityDefinition>(ResourceLocation::compareNamespaced);
        ir.definitions().forEach((key, canonical) -> {
            if (key.kind().equals(DefinitionKinds.ABILITY)) {
                if (abilities.size() >= MAX_ABILITIES) {
                    throw new IllegalArgumentException("Ability count exceeds " + MAX_ABILITIES);
                }
                AbilityDefinition definition = AbilityCanonicalCodec.decode(canonical);
                if (abilities.putIfAbsent(definition.id(), definition) != null) {
                    throw new IllegalArgumentException("Duplicate ability id " + definition.id());
                }
            }
        });
        validateDefinitions(abilities, skills);
        validateClassGrants(abilities, classes);
        validateGlobalBounds(abilities);
        return new AbilityCatalog(abilities);
    }

    private static void validateDefinitions(
            Map<ResourceLocation, AbilityDefinition> abilities,
            SkillCatalog skills
    ) {
        for (AbilityDefinition ability : abilities.values()) {
            for (AbilityPersistentEffect effect : ability.persistentEffects()) {
                if (effect instanceof AbilityAttributeEffect attributeEffect) {
                    if (BuiltInRegistries.ATTRIBUTE.getHolder(attributeEffect.attribute()).isEmpty()) {
                        throw new IllegalArgumentException("Ability " + ability.id()
                                + " references missing attribute " + attributeEffect.attribute());
                    }
                    if (!AttributeProjectionSafety.isWithinBounds(
                            attributeEffect.operation(), attributeEffect.valueUnits())) {
                        throw new IllegalArgumentException("Ability " + ability.id()
                                + " attribute effect exceeds its projection safety bound "
                                + attributeEffect.id());
                    }
                }
            }
            for (AbilityCost cost : ability.costs()) {
                if (cost instanceof AbilityCurrencyCost currencyCost) {
                    var currency = skills.currency(currencyCost.currency()).orElseThrow(() ->
                            new IllegalArgumentException("Ability " + ability.id()
                                    + " references missing currency " + currencyCost.currency()));
                    if (currencyCost.amount() > currency.maximum()) {
                        throw new IllegalArgumentException("Ability " + ability.id()
                                + " cost exceeds currency maximum " + currencyCost.currency());
                    }
                }
            }
            for (AbilityAction action : ability.actions()) {
                if (action instanceof AbilityVanillaEffectAction effectAction
                        && BuiltInRegistries.MOB_EFFECT.getHolder(effectAction.effect()).isEmpty()) {
                    throw new IllegalArgumentException("Ability " + ability.id()
                            + " references missing vanilla effect " + effectAction.effect());
                }
            }
        }
    }

    private static void validateClassGrants(
            Map<ResourceLocation, AbilityDefinition> abilities,
            ClassCatalog classes
    ) {
        classes.classes().values().forEach(definition -> definition.grants().forEach(grant ->
                validateClassGrant(abilities, definition.id(), grant)));
        classes.synergies().values().forEach(synergy -> synergy.grants().forEach(grant ->
                validateClassGrant(abilities, synergy.id(), grant)));
    }

    private static void validateClassGrant(
            Map<ResourceLocation, AbilityDefinition> abilities,
            ResourceLocation owner,
            ClassGrant grant
    ) {
        if (grant.type() == ClassGrantType.ABILITY && !abilities.containsKey(grant.targetId())) {
            throw new IllegalArgumentException("Class ability grant " + grant.id()
                    + " references missing ability " + grant.targetId() + " from " + owner);
        }
    }

    private static void validateGlobalBounds(Map<ResourceLocation, AbilityDefinition> abilities) {
        int effects = 0;
        int costs = 0;
        int actions = 0;
        for (AbilityDefinition ability : abilities.values()) {
            effects = Math.addExact(effects, ability.persistentEffects().size());
            costs = Math.addExact(costs, ability.costs().size());
            actions = Math.addExact(actions, ability.actions().size());
        }
        if (effects > MAX_TOTAL_PERSISTENT_EFFECTS) {
            throw new IllegalArgumentException("Ability persistent effect count exceeds "
                    + MAX_TOTAL_PERSISTENT_EFFECTS);
        }
        if (costs > MAX_TOTAL_COSTS) {
            throw new IllegalArgumentException("Ability cost count exceeds " + MAX_TOTAL_COSTS);
        }
        if (actions > MAX_TOTAL_ACTIONS) {
            throw new IllegalArgumentException("Ability action count exceeds " + MAX_TOTAL_ACTIONS);
        }
    }

    public Map<ResourceLocation, AbilityDefinition> abilities() {
        return abilities;
    }

    public Optional<AbilityDefinition> ability(ResourceLocation id) {
        return Optional.ofNullable(abilities.get(id));
    }

    public String abilityLineageFingerprint(ResourceLocation abilityId) {
        AbilityDefinition definition = ability(abilityId).orElseThrow(
                () -> new IllegalArgumentException("Unknown ability " + abilityId));
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                write(output, "progressiveskills-ability-lineage-v1");
                write(output, definition.id().toString());
                write(output, definition.kind().serializedName());
                output.writeBoolean(definition.slotAllowed());
                output.writeInt(definition.persistentEffects().size());
                for (AbilityPersistentEffect effect : definition.persistentEffects()) {
                    write(output, effect.id().toString());
                    write(output, effect.type().serializedName());
                    write(output, effect.targetType().toString());
                    write(output, effect.targetId().toString());
                }
                output.writeInt(definition.actions().size());
                for (AbilityAction action : definition.actions()) {
                    write(output, action.id().toString());
                    write(output, action.type().serializedName());
                }
            }
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in memory lineage failure", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
