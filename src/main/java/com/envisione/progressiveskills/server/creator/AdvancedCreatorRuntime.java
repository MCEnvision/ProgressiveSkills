package com.envisione.progressiveskills.server.creator;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.creator.CreatorDefinition;
import com.envisione.progressiveskills.common.creator.CreatorPredicateEngine;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementMutation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AdvancedCreatorRuntime {
    private static final ResourceLocation RANK_VALUE = ResourceLocation.fromNamespaceAndPath(
            ProjectIdentity.MOD_ID, "creator_rank");
    private static final ResourceLocation EXCLUSIVE_GROUP = ResourceLocation.fromNamespaceAndPath(
            ProjectIdentity.MOD_ID, "creator_exclusive_group");
    private static final ResourceLocation STANCE_GROUP = ResourceLocation.fromNamespaceAndPath(
            ProjectIdentity.MOD_ID, "creator_stance_group");

    private AdvancedCreatorRuntime() {
    }

    public static TransactionResult purchaseRank(
            ServerPlayer player,
            DefinitionKind kind,
            ResourceLocation rankId,
            UUID operationId
    ) {
        if (!kind.equals(DefinitionKinds.TREE_RANK) && !kind.equals(DefinitionKinds.CLASS_RANK)) {
            throw new IllegalArgumentException("Creator rank kind is invalid");
        }
        CreatorDefinition definition = CreatorRuntime.catalog().flatMap(value ->
                value.definition(kind, rankId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown creator rank " + rankId));
        var context = TransactionRuntime.context(player.getServer()).orElseThrow();
        var revision = TransactionRuntime.currentDefinition().orElseThrow();
        var snapshot = context.service().snapshot(player.getUUID());
        EntitlementKey rankKey = new EntitlementKey(RANK_VALUE, rankId);
        int current = Math.toIntExact(snapshot.projectedValues().getOrDefault(rankKey, 0L));
        int maximum = Math.toIntExact(definition.integer("maximum_rank").orElse(1L));
        if (current >= maximum) {
            throw new IllegalStateException("Creator rank is already at maximum");
        }
        int next = Math.addExact(current, 1);
        ResourceLocation currency = definition.id("cost_currency").orElseThrow();
        long baseCost = definition.integer("base_cost").orElse(1L);
        long growth = definition.integer("cost_growth").orElse(0L);
        long cost = Math.addExact(baseCost, Math.multiplyExact(growth, current));
        SkillCatalog skills = PackRuntime.service().map(service -> SkillCatalog.from(
                service.live().snapshot().canonicalIr())).orElseThrow();
        var currencyDefinition = skills.currency(currency).orElseThrow(
                () -> new IllegalArgumentException("Unknown creator rank currency " + currency));
        long storedCurrency = snapshot.balances().getOrDefault(currency, 0L);
        long logicalCurrency = snapshot.balances().getOrDefault(currency, currencyDefinition.initial());
        long currencyAfter = Math.subtractExact(logicalCurrency, cost);
        long currencyDelta = Math.subtractExact(currencyAfter, storedCurrency);
        GrantSourceId rankSource = new GrantSourceId(kind.id(), rankId, nested(rankId, "rank"));
        var entitlements = new ArrayList<EntitlementMutation>();
        entitlements.add(EntitlementMutation.grant(rankKey, rankSource, next, EntitlementResolver.HIGHEST));
        definition.id("exclusive_group").ifPresent(group -> {
            EntitlementKey groupKey = new EntitlementKey(EXCLUSIVE_GROUP, group);
            boolean conflicting = snapshot.ownership().getOrDefault(groupKey, Map.of()).keySet().stream()
                    .anyMatch(source -> !source.equals(rankSource));
            if (conflicting) {
                throw new IllegalStateException("Creator rank conflicts with exclusive group " + group);
            }
            entitlements.add(EntitlementMutation.grant(
                    groupKey, rankSource, 1, EntitlementResolver.BOOLEAN_UNION));
        });
        TransactionStep step = new TransactionStep(
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "creator/rank"),
                List.of(new BalanceMutation(currency, currencyDelta,
                        currencyDefinition.minimum(), currencyDefinition.maximum())),
                entitlements, List.of(), List.of());
        TransactionPlan plan = new TransactionPlan(
                player.getUUID(), player.getUUID(), new IdempotencyKey("creator.rank." + operationId),
                snapshot.stateRevision(), revision, ProgressionCause.GAMEPLAY,
                "Purchase creator rank", step);
        return context.executeAndPersist(player, CascadePlan.single(plan), revision);
    }

    public static TransactionResult selectStance(
            ServerPlayer player,
            ResourceLocation stanceId,
            UUID operationId
    ) {
        CreatorDefinition definition = CreatorRuntime.catalog().flatMap(value ->
                value.definition(DefinitionKinds.STANCE, stanceId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown stance " + stanceId));
        ResourceLocation group = definition.id("group").orElseThrow();
        var context = TransactionRuntime.context(player.getServer()).orElseThrow();
        var revision = TransactionRuntime.currentDefinition().orElseThrow();
        var snapshot = context.service().snapshot(player.getUUID());
        EntitlementKey key = new EntitlementKey(STANCE_GROUP, group);
        GrantSourceId source = new GrantSourceId(DefinitionKinds.STANCE.id(), stanceId, nested(stanceId, "active"));
        var mutations = new ArrayList<EntitlementMutation>();
        snapshot.ownership().getOrDefault(key, Map.of()).keySet().forEach(existing ->
                mutations.add(EntitlementMutation.revoke(key, existing)));
        mutations.add(EntitlementMutation.grant(key, source, 1, EntitlementResolver.BOOLEAN_UNION));
        TransactionStep step = new TransactionStep(
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "creator/stance"),
                List.of(), mutations, List.of(), List.of());
        TransactionPlan plan = new TransactionPlan(
                player.getUUID(), player.getUUID(), new IdempotencyKey("creator.stance." + operationId),
                snapshot.stateRevision(), revision, ProgressionCause.GAMEPLAY,
                "Select creator stance", step);
        return context.executeAndPersist(player, CascadePlan.single(plan), revision);
    }

    public static ProcResult triggerProc(
            ServerPlayer player,
            ResourceLocation procId,
            ResourceLocation trigger,
            long eventSeed
    ) {
        CreatorDefinition definition = CreatorRuntime.catalog().flatMap(value ->
                value.definition(DefinitionKinds.REACTIVE_PROC, procId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown reactive proc " + procId));
        if (!definition.id("trigger").orElseThrow().equals(trigger)) {
            return new ProcResult(false, "Trigger does not match", 0L);
        }
        long tick = player.level().getGameTime();
        ResourceLocation readyKey = internalId("proc_ready", procId);
        CreatorProgressSavedData data = CreatorProgressSavedData.get(player.getServer());
        long ready = data.state(player.getUUID()).resources().getOrDefault(readyKey, 0L);
        if (tick < ready) {
            return new ProcResult(false, "Proc is on cooldown", ready - tick);
        }
        long chance = definition.integer("chance_basis_points").orElse(10_000L);
        if (chance < 0 || chance > 10_000) {
            throw new IllegalArgumentException("Reactive proc chance is invalid");
        }
        long roll = Math.floorMod(eventSeed ^ player.getUUID().getMostSignificantBits() ^ procId.hashCode(), 10_000L);
        if (roll >= chance) {
            return new ProcResult(false, "Proc roll did not pass", 0L);
        }
        long cooldown = definition.integer("cooldown_ticks").orElse(0L);
        Optional<CreatorProgressSavedData.ResourceChange> change = resourceChange(definition);
        CreatorProgressSavedData.TimedAwardResult result = data.applyTimedResourceChange(
                player.getUUID(), readyKey, tick, cooldown, change);
        return new ProcResult(result.applied(), result.applied() ? "Proc activated" : "Proc is on cooldown",
                result.cooldownTicks());
    }

    public static CreatorPredicateEngine.Result predicate(
            ServerPlayer player,
            ResourceLocation predicateId
    ) {
        var values = new LinkedHashMap<ResourceLocation, Long>();
        var transaction = TransactionRuntime.context(player.getServer()).orElseThrow()
                .service().snapshot(player.getUUID());
        values.putAll(transaction.balances());
        values.putAll(CreatorProgressSavedData.get(player.getServer()).state(player.getUUID()).resources());
        return CreatorPredicateEngine.evaluate(CreatorRuntime.catalog().orElseThrow(), predicateId,
                new CreatorPredicateEngine.Context(values, Set.of()));
    }

    public static long progressChallenge(
            ServerPlayer player,
            ResourceLocation challengeId,
            long amount
    ) {
        CreatorDefinition definition = CreatorRuntime.catalog().flatMap(value ->
                value.definition(DefinitionKinds.CHALLENGE, challengeId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown challenge " + challengeId));
        long goal = definition.integer("goal").orElseThrow();
        return CreatorProgressSavedData.get(player.getServer()).progressChallenge(
                player.getUUID(), challengeId, amount, goal);
    }

    public static boolean applyContextEffect(ServerPlayer player, ResourceLocation effectId) {
        CreatorDefinition definition = CreatorRuntime.catalog().flatMap(value ->
                value.definition(DefinitionKinds.CONTEXT_EFFECT, effectId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown context effect " + effectId));
        ResourceLocation predicate = definition.id("predicate").orElseThrow();
        if (!predicate(player, predicate).passed()) {
            return false;
        }
        long tick = player.level().getGameTime();
        long cooldown = definition.integer("cooldown_ticks").orElse(20L);
        ResourceLocation readyKey = internalId("context_ready", effectId);
        return CreatorProgressSavedData.get(player.getServer()).applyTimedResourceChange(
                player.getUUID(), readyKey, tick, cooldown, resourceChange(definition)).applied();
    }

    private static Optional<CreatorProgressSavedData.ResourceChange> resourceChange(
            CreatorDefinition definition
    ) {
        return definition.id("resource").map(resource -> {
            CreatorDefinition resourceDefinition = CreatorRuntime.catalog().flatMap(value ->
                    value.definition(DefinitionKinds.RESOURCE, resource)).orElseThrow(
                    () -> new IllegalArgumentException("Unknown creator resource " + resource));
            long minimum = resourceDefinition.integer("minimum").orElse(0L);
            long maximum = resourceDefinition.integer("maximum").orElseThrow();
            long initial = resourceDefinition.integer("initial").orElse(minimum);
            var configuredAmount = definition.integer("resource_amount");
            long amount = configuredAmount.isPresent()
                    ? configuredAmount.getAsLong() : definition.integer("amount").orElse(1L);
            return new CreatorProgressSavedData.ResourceChange(
                    resource, amount, minimum, maximum, initial);
        });
    }

    private static ResourceLocation nested(ResourceLocation id, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "/" + suffix);
    }

    private static ResourceLocation internalId(String prefix, ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID,
                prefix + "/" + digest(id.toString()).substring(0, 24));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ProcResult(boolean activated, String reason, long cooldownTicks) {
    }
}
