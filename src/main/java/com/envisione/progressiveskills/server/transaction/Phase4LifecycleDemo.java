package com.envisione.progressiveskills.server.transaction;

import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.common.transaction.DeliveryContract;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementMutation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.RepeatPolicy;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.transaction.TransitionAction;
import com.envisione.progressiveskills.common.transaction.TransitionFailurePolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Session-only Phase 4 fixture that makes lifecycle behavior observable before Phase 5 persistence. */
public final class Phase4LifecycleDemo {
    public static final ResourceLocation DEMO_POINTS = id("demo_points");
    public static final EntitlementKey MAX_HEALTH = new EntitlementKey(
            id("attribute"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "generic.max_health")
    );
    public static final GrantSourceId PRIMARY_HEALTH = source("primary_health");
    public static final GrantSourceId SECONDARY_HEALTH = source("secondary_health");
    public static final ResourceLocation ITEM_ACTION_TYPE = id("item");
    public static final ResourceLocation HEALTH_MODIFIER_ID = id("phase4_demo_health");
    public static final long HEALTH_BONUS = 4;

    private static final ResourceLocation ORIGIN = id("phase4_lifecycle_demo");
    private static final TransitionAction STARTER_ITEM = new TransitionAction(
            ITEM_ACTION_TYPE,
            source("starter_item"),
            "minecraft:gold_ingot",
            1,
            RepeatPolicy.ONCE_PER_CHARACTER,
            DeliveryContract.EFFECTIVELY_ONCE,
            TransitionFailurePolicy.STOP
    );

    private Phase4LifecycleDemo() {
    }

    public static CascadePlan primary(UUID actor, UUID target, ProgressionSnapshot state, DefinitionRevision definition) {
        return plan(
                actor,
                target,
                state,
                definition,
                "phase4/demo/" + target,
                "Phase 4 primary lifecycle demonstration",
                List.of(new BalanceMutation(DEMO_POINTS, 1, 0, 1)),
                List.of(EntitlementMutation.grant(
                        MAX_HEALTH, PRIMARY_HEALTH, HEALTH_BONUS, EntitlementResolver.HIGHEST
                )),
                List.of(STARTER_ITEM)
        );
    }

    public static CascadePlan coowner(UUID actor, UUID target, ProgressionSnapshot state, DefinitionRevision definition) {
        return plan(
                actor,
                target,
                state,
                definition,
                "phase4/coowner/" + target,
                "Phase 4 secondary ownership demonstration",
                List.of(),
                List.of(EntitlementMutation.grant(
                        MAX_HEALTH, SECONDARY_HEALTH, HEALTH_BONUS, EntitlementResolver.HIGHEST
                )),
                List.of()
        );
    }

    public static CascadePlan revoke(
            UUID actor,
            UUID target,
            ProgressionSnapshot state,
            DefinitionRevision definition,
            boolean primary
    ) {
        String sourceName = primary ? "primary" : "secondary";
        return plan(
                actor,
                target,
                state,
                definition,
                "phase4/revoke-" + sourceName + "/" + target,
                "Phase 4 " + sourceName + " source revocation",
                List.of(),
                List.of(EntitlementMutation.revoke(MAX_HEALTH, primary ? PRIMARY_HEALTH : SECONDARY_HEALTH)),
                List.of()
        );
    }

    private static CascadePlan plan(
            UUID actor,
            UUID target,
            ProgressionSnapshot state,
            DefinitionRevision definition,
            String key,
            String reason,
            List<BalanceMutation> balances,
            List<EntitlementMutation> entitlements,
            List<TransitionAction> actions
    ) {
        var step = new TransactionStep(ORIGIN, balances, entitlements, actions);
        return CascadePlan.single(new TransactionPlan(
                actor,
                target,
                new IdempotencyKey(key),
                state.stateRevision(),
                definition,
                ProgressionCause.ADMIN,
                reason,
                step
        ));
    }

    private static GrantSourceId source(String grant) {
        return new GrantSourceId(
                id("manual"),
                id("phase4_demo"),
                id("phase4_demo/" + grant)
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", path);
    }
}
