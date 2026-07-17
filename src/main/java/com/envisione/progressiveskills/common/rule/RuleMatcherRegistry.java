package com.envisione.progressiveskills.common.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class RuleMatcherRegistry {
    private final Map<String, MatcherType> matchers;

    private RuleMatcherRegistry(Map<String, MatcherType> matchers) {
        this.matchers = Collections.unmodifiableMap(new LinkedHashMap<>(matchers));
    }

    public static RuleMatcherRegistry core() {
        return builder()
                .register("id", Set.of(RuleSubjectType.BLOCK, RuleSubjectType.ENTITY, RuleSubjectType.ITEM))
                .register("tag", Set.of(RuleSubjectType.BLOCK, RuleSubjectType.ENTITY, RuleSubjectType.ITEM))
                .register("mod", Set.of(RuleSubjectType.BLOCK, RuleSubjectType.ENTITY, RuleSubjectType.ITEM))
                .register("translation_key", Set.of(
                        RuleSubjectType.BLOCK, RuleSubjectType.ENTITY, RuleSubjectType.ITEM
                ))
                .register("custom_name", Set.of(RuleSubjectType.ENTITY, RuleSubjectType.ITEM))
                .register("school", Set.of(RuleSubjectType.SPELL))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<MatcherType> find(String prefix) {
        return Optional.ofNullable(matchers.get(prefix));
    }

    public MatcherType require(String prefix) {
        return find(prefix).orElseThrow(() -> new IllegalArgumentException("Unknown rule matcher " + prefix));
    }

    public Map<String, MatcherType> matchers() {
        return matchers;
    }

    public record MatcherType(String prefix, Set<RuleSubjectType> subjects) {
        public MatcherType {
            prefix = Objects.requireNonNull(prefix, "prefix");
            subjects = Set.copyOf(Objects.requireNonNull(subjects, "subjects"));
            if (!prefix.matches("[a-z][a-z0-9_]*") || subjects.isEmpty()) {
                throw new IllegalArgumentException("Invalid rule matcher registration " + prefix);
            }
        }

        public boolean supports(RuleSubjectType subject) {
            return subjects.contains(subject);
        }
    }

    public static final class Builder {
        private final Map<String, MatcherType> matchers = new TreeMap<>();

        public Builder register(String prefix, Set<RuleSubjectType> subjects) {
            MatcherType matcher = new MatcherType(prefix, subjects);
            if (matchers.putIfAbsent(prefix, matcher) != null) {
                throw new IllegalArgumentException("Duplicate rule matcher " + prefix);
            }
            return this;
        }

        public RuleMatcherRegistry build() {
            if (matchers.isEmpty()) {
                throw new IllegalArgumentException("Rule matcher registry must not be empty");
            }
            return new RuleMatcherRegistry(new TreeMap<>(matchers));
        }
    }
}
