package com.envisione.progressiveskills.server.rule;

import com.envisione.progressiveskills.common.rule.RuleCatalog;
import com.envisione.progressiveskills.common.rule.RuleDefinition;
import com.envisione.progressiveskills.common.rule.RuleMatcherSpec;
import com.envisione.progressiveskills.common.rule.RuleTriggerRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockRuleTable {
    private static final Comparator<CompiledRule> ORDER = Comparator
            .comparingInt((CompiledRule value) -> value.rule().priority()).reversed()
            .thenComparing(value -> value.rule().id(), ResourceLocation::compareNamespaced);
    private final Map<ResourceLocation, CompiledRule[]> exact;
    private final Map<String, CompiledRule[]> namespaces;
    private final Map<String, CompiledRule[]> translations;
    private final List<TagBucket> tags;
    private final CompiledRule[] general;
    private final int[] seen;
    private int sequence;

    private BlockRuleTable(
            Map<ResourceLocation, CompiledRule[]> exact,
            Map<String, CompiledRule[]> namespaces,
            Map<String, CompiledRule[]> translations,
            List<TagBucket> tags,
            CompiledRule[] general,
            int ruleCount
    ) {
        this.exact = Map.copyOf(exact);
        this.namespaces = Map.copyOf(namespaces);
        this.translations = Map.copyOf(translations);
        this.tags = List.copyOf(tags);
        this.general = general;
        this.seen = new int[ruleCount];
    }

    public static BlockRuleTable compile(RuleCatalog catalog) {
        var exact = new HashMap<ResourceLocation, List<CompiledRule>>();
        var namespaces = new HashMap<String, List<CompiledRule>>();
        var translations = new HashMap<String, List<CompiledRule>>();
        var tags = new LinkedHashMap<TagKey<Block>, List<CompiledRule>>();
        var general = new ArrayList<CompiledRule>();
        int index = 0;
        for (RuleDefinition rule : catalog.rules().values()) {
            if (!rule.enabled() || !rule.trigger().equals(RuleTriggerRegistry.BLOCK_BREAK)) {
                continue;
            }
            CompiledRule compiled = new CompiledRule(
                    index++,
                    rule,
                    rule.multipliedBaseUnits(),
                    rule.matchers().stream().map(CompiledMatcher::compile).toList()
            );
            boolean indexed = false;
            for (CompiledMatcher matcher : compiled.matchers()) {
                if (matcher.negated()) {
                    continue;
                }
                indexed = true;
                switch (matcher.prefix()) {
                    case "id" -> exact.computeIfAbsent(matcher.id(), ignored -> new ArrayList<>()).add(compiled);
                    case "mod" -> namespaces.computeIfAbsent(matcher.text(), ignored -> new ArrayList<>()).add(compiled);
                    case "translation_key" -> translations.computeIfAbsent(
                            matcher.text(), ignored -> new ArrayList<>()
                    ).add(compiled);
                    case "tag" -> tags.computeIfAbsent(
                            matcher.tag(), ignored -> new ArrayList<>()
                    ).add(compiled);
                    default -> throw new IllegalArgumentException(
                            "Unsupported block matcher " + matcher.prefix()
                    );
                }
            }
            if (!indexed) {
                general.add(compiled);
            }
        }
        var tagBuckets = new ArrayList<TagBucket>();
        tags.forEach((tag, rules) -> tagBuckets.add(new TagBucket(tag, array(rules))));
        return new BlockRuleTable(
                arrays(exact),
                arrays(namespaces),
                arrays(translations),
                tagBuckets,
                array(general),
                index
        );
    }

    public boolean empty() {
        return seen.length == 0;
    }

    public List<CompiledRule> match(BlockState state) {
        if (empty()) {
            return List.of();
        }
        int marker = nextSequence();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        CompiledRule[] exactRules = exact.get(blockId);
        CompiledRule[] namespaceRules = namespaces.get(blockId.getNamespace());
        CompiledRule[] translationRules = translations.get(state.getBlock().getDescriptionId());
        boolean any = exactRules != null || namespaceRules != null || translationRules != null || general.length > 0;
        if (!any) {
            for (TagBucket bucket : tags) {
                if (state.is(bucket.tag())) {
                    any = true;
                    break;
                }
            }
        }
        if (!any) {
            return List.of();
        }
        var candidates = new ArrayList<CompiledRule>();
        add(exactRules, marker, candidates);
        add(namespaceRules, marker, candidates);
        add(translationRules, marker, candidates);
        for (TagBucket bucket : tags) {
            if (state.is(bucket.tag())) {
                add(bucket.rules(), marker, candidates);
            }
        }
        add(general, marker, candidates);
        candidates.removeIf(candidate -> !matches(candidate, state, blockId));
        candidates.sort(ORDER);
        return List.copyOf(candidates);
    }

    private boolean matches(CompiledRule rule, BlockState state, ResourceLocation blockId) {
        boolean hasPositive = false;
        boolean positiveMatch = false;
        for (CompiledMatcher matcher : rule.matchers()) {
            boolean matched = matcher.matches(state, blockId);
            if (matcher.negated() && matched) {
                return false;
            }
            if (!matcher.negated()) {
                hasPositive = true;
                positiveMatch |= matched;
            }
        }
        return !hasPositive || positiveMatch;
    }

    private void add(CompiledRule[] rules, int marker, ArrayList<CompiledRule> result) {
        if (rules == null) {
            return;
        }
        for (CompiledRule rule : rules) {
            if (seen[rule.index()] != marker) {
                seen[rule.index()] = marker;
                result.add(rule);
            }
        }
    }

    private int nextSequence() {
        sequence++;
        if (sequence == 0) {
            java.util.Arrays.fill(seen, 0);
            sequence = 1;
        }
        return sequence;
    }

    private static <K> Map<K, CompiledRule[]> arrays(Map<K, List<CompiledRule>> source) {
        var result = new HashMap<K, CompiledRule[]>();
        source.forEach((key, value) -> result.put(key, array(value)));
        return result;
    }

    private static CompiledRule[] array(List<CompiledRule> rules) {
        rules.sort(ORDER);
        return rules.toArray(CompiledRule[]::new);
    }

    public record CompiledRule(
            int index,
            RuleDefinition rule,
            long multipliedBaseUnits,
            List<CompiledMatcher> matchers
    ) {
    }

    private record TagBucket(TagKey<Block> tag, CompiledRule[] rules) {
    }

    public record CompiledMatcher(
            String prefix,
            ResourceLocation id,
            TagKey<Block> tag,
            String text,
            boolean negated
    ) {
        static CompiledMatcher compile(RuleMatcherSpec matcher) {
            return switch (matcher.prefix()) {
                case "id" -> new CompiledMatcher("id", matcher.idValue(), null, null, matcher.negated());
                case "tag" -> new CompiledMatcher(
                        "tag",
                        null,
                        TagKey.create(Registries.BLOCK, matcher.idValue()),
                        null,
                        matcher.negated()
                );
                case "mod", "translation_key" -> new CompiledMatcher(
                        matcher.prefix(), null, null, matcher.value(), matcher.negated()
                );
                default -> throw new IllegalArgumentException("Unsupported block matcher " + matcher.prefix());
            };
        }

        boolean matches(BlockState state, ResourceLocation blockId) {
            return switch (prefix) {
                case "id" -> blockId.equals(id);
                case "tag" -> state.is(tag);
                case "mod" -> blockId.getNamespace().equals(text);
                case "translation_key" -> state.getBlock().getDescriptionId().equals(text);
                default -> false;
            };
        }
    }
}
