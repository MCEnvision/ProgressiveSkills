package com.envisione.progressiveskills.common.rule;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public final class RuleStackResolver {
    private static final Comparator<Candidate> ORDER = Comparator
            .comparingInt((Candidate candidate) -> candidate.rule().priority()).reversed()
            .thenComparing(candidate -> candidate.rule().id(), ResourceLocation::compareNamespaced);

    private RuleStackResolver() {
    }

    public static List<Candidate> resolve(List<Candidate> candidates) {
        var groups = new TreeMap<ResourceLocation, List<Candidate>>(ResourceLocation::compareNamespaced);
        for (Candidate candidate : candidates) {
            groups.computeIfAbsent(candidate.rule().stackGroup(), ignored -> new ArrayList<>()).add(candidate);
        }
        var result = new ArrayList<Candidate>();
        for (List<Candidate> group : groups.values()) {
            group.sort(ORDER);
            RuleStackRule policy = group.getFirst().rule().stackRule();
            if (group.stream().anyMatch(candidate -> candidate.rule().stackRule() != policy)) {
                throw new IllegalArgumentException("A rule stack group must use one policy");
            }
            switch (policy) {
                case SUM -> result.addAll(group);
                case HIGHEST -> result.add(group.stream().max(
                        Comparator.comparingLong(Candidate::amountUnits).thenComparing(ORDER.reversed())
                ).orElseThrow());
                case FIRST, EXCLUSIVE -> result.add(group.getFirst());
                case DIMINISHING -> {
                    for (int index = 0; index < group.size(); index++) {
                        Candidate candidate = group.get(index);
                        long diminished = Math.max(1, candidate.amountUnits() / (index + 1L));
                        result.add(new Candidate(candidate.rule(), diminished));
                    }
                }
            }
        }
        result.sort(ORDER);
        return List.copyOf(result);
    }

    public record Candidate(RuleDefinition rule, long amountUnits) {
        public Candidate {
            if (amountUnits <= 0) {
                throw new IllegalArgumentException("Rule candidate amount must be positive");
            }
        }
    }
}
