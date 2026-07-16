package com.envisione.progressiveskills.common.transaction;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.minecraft.resources.ResourceLocation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionTransactionPropertyTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final DefinitionRevision DEFINITIONS = new DefinitionRevision(1, "c".repeat(64));
    private static final EntitlementKey KEY = new EntitlementKey(
            ResourceLocation.parse("progressiveskills:test_value"),
            ResourceLocation.parse("progressiveskills:highest")
    );

    @Property(tries = 1_000)
    void highestOwnershipResolutionIsIndependentOfGrantOrder(
            @ForAll @IntRange(min = -1_000_000, max = 1_000_000) int first,
            @ForAll @IntRange(min = -1_000_000, max = 1_000_000) int second,
            @ForAll boolean reverse
    ) {
        var mutations = new ArrayList<EntitlementMutation>();
        mutations.add(EntitlementMutation.grant(KEY, source("a"), first, EntitlementResolver.HIGHEST));
        mutations.add(EntitlementMutation.grant(KEY, source("b"), second, EntitlementResolver.HIGHEST));
        if (reverse) {
            Collections.reverse(mutations);
        }
        var service = new ProgressionTransactionService(1, 8, 8, 8, Clock.systemUTC());
        var step = new TransactionStep(
                ResourceLocation.parse("progressiveskills:property"),
                List.of(), mutations, List.of()
        );
        var plan = new TransactionPlan(
                TARGET, TARGET, new IdempotencyKey("property/highest"), 0, DEFINITIONS,
                ProgressionCause.GAMEPLAY, "Property resolution", step
        );
        service.execute(CascadePlan.single(plan), DEFINITIONS, new NoopProjector(), new NoopExecutor());

        assertEquals((long) Math.max(first, second), service.snapshot(TARGET).projectedValues().get(KEY));
    }

    private static GrantSourceId source(String path) {
        return new GrantSourceId(
                ResourceLocation.parse("progressiveskills:test_owner"),
                ResourceLocation.parse("progressiveskills:property"),
                ResourceLocation.parse("progressiveskills:" + path)
        );
    }

    private static final class NoopProjector implements PersistentProjector {
        @Override
        public Optional<String> validate(UUID targetId, List<ProjectionChange> changes) {
            return Optional.empty();
        }

        @Override
        public void apply(UUID targetId, List<ProjectionChange> changes) {
            // Pure property test has no physical projection.
        }
    }

    private static final class NoopExecutor implements TransitionActionExecutor {
        @Override
        public Optional<String> validate(UUID targetId, TransitionAction action) {
            return Optional.empty();
        }

        @Override
        public ActionExecution execute(UUID targetId, TransactionId transactionId, TransitionAction action) {
            return ActionExecution.success("noop");
        }
    }
}
