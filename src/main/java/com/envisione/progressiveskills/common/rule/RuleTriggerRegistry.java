package com.envisione.progressiveskills.common.rule;

import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class RuleTriggerRegistry {
    public static final ResourceLocation BLOCK_BREAK = id("block_break");
    private final Map<ResourceLocation, Trigger> triggers;

    private RuleTriggerRegistry(Map<ResourceLocation, Trigger> triggers) {
        this.triggers = Collections.unmodifiableMap(new LinkedHashMap<>(triggers));
    }

    public static RuleTriggerRegistry core() {
        return builder().register(BLOCK_BREAK, RuleSubjectType.BLOCK).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Trigger> find(ResourceLocation id) {
        return Optional.ofNullable(triggers.get(id));
    }

    public Trigger require(ResourceLocation id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown rule trigger " + id));
    }

    public Map<ResourceLocation, Trigger> triggers() {
        return triggers;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }

    public record Trigger(ResourceLocation id, RuleSubjectType subjectType) {
        public Trigger {
            id = StableId.requireValid(id);
            Objects.requireNonNull(subjectType, "subjectType");
        }
    }

    public static final class Builder {
        private final Map<ResourceLocation, Trigger> triggers = new TreeMap<>(ResourceLocation::compareNamespaced);

        public Builder register(ResourceLocation id, RuleSubjectType subjectType) {
            Trigger trigger = new Trigger(id, subjectType);
            if (triggers.putIfAbsent(trigger.id(), trigger) != null) {
                throw new IllegalArgumentException("Duplicate rule trigger " + id);
            }
            return this;
        }

        public RuleTriggerRegistry build() {
            if (triggers.isEmpty()) {
                throw new IllegalArgumentException("Rule trigger registry must not be empty");
            }
            return new RuleTriggerRegistry(new TreeMap<>(triggers));
        }
    }
}
