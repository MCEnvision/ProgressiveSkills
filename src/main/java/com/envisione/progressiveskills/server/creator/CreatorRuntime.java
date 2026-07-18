package com.envisione.progressiveskills.server.creator;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.ability.AbilityCatalog;
import com.envisione.progressiveskills.common.ability.AbilityProgression;
import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.common.classdef.ClassCatalog;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.creator.BuildShareCode;
import com.envisione.progressiveskills.common.creator.CreatorCatalog;
import com.envisione.progressiveskills.common.creator.CreatorDefinition;
import com.envisione.progressiveskills.common.creator.CreatorFormulaParser;
import com.envisione.progressiveskills.common.expression.ExpressionRounding;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.skill.CurrencyDefinition;
import com.envisione.progressiveskills.common.skill.SkillProgress;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.transaction.BalanceMutation;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.ProgressionSnapshot;
import com.envisione.progressiveskills.common.transaction.ProgressionTransactionService;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.tree.TreeDefinition;
import com.envisione.progressiveskills.common.tree.TreeProgression;
import com.envisione.progressiveskills.server.ability.AbilityRuntime;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class CreatorRuntime {
    private static final AtomicReference<CachedCatalog> CACHED = new AtomicReference<>();

    private CreatorRuntime() {
    }

    public static Optional<CreatorCatalog> catalog() {
        return PackRuntime.service().map(service -> {
            long generation = service.live().generation();
            String digest = service.live().snapshot().contentDigest();
            CachedCatalog current = CACHED.get();
            if (current != null && current.generation() == generation && current.digest().equals(digest)) {
                return current.catalog();
            }
            CreatorCatalog created = CreatorCatalog.from(service.live().snapshot().canonicalIr());
            CACHED.set(new CachedCatalog(generation, digest, created));
            return created;
        });
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        long tick = event.getServer().overworld().getGameTime();
        Optional<CreatorCatalog> available = catalog();
        if (available.isEmpty()) {
            return;
        }
        CreatorProgressSavedData data = CreatorProgressSavedData.get(event.getServer());
        if (!data.active()) {
            return;
        }
        for (CreatorDefinition resource : available.orElseThrow().kind(DefinitionKinds.RESOURCE)) {
            long decay = resource.integer("decay_amount").orElse(0L);
            long interval = resource.integer("decay_interval_ticks").orElse(0L);
            if (decay < 1 || interval < 1 || Math.floorMod(tick, interval) != 0) {
                continue;
            }
            long minimum = resource.integer("minimum").orElse(0L);
            long maximum = resource.integer("maximum").orElseThrow();
            long initial = resource.integer("initial").orElse(minimum);
            event.getServer().getPlayerList().getPlayers().forEach(player -> data.adjustResource(
                    player.getUUID(), resource.key().id(), Math.negateExact(decay), minimum, maximum, initial));
        }
    }

    public static long simulate(ServerPlayer player, String formula) {
        var expression = CreatorFormulaParser.compile(formula, ExpressionRounding.FLOOR);
        CreatorCatalog catalog = catalog().orElseThrow(
                () -> new IllegalStateException("Creator catalog is unavailable"));
        var context = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var snapshot = context.service().snapshot(player.getUUID());
        return expression.evaluate(dependency -> {
            Optional<CreatorDefinition> variable = catalog.definition(DefinitionKinds.VARIABLE, dependency.id());
            if (variable.isPresent()) {
                return variable.orElseThrow().integer("value");
            }
            Long balance = snapshot.balances().get(dependency.id());
            return balance == null ? OptionalLong.empty() : OptionalLong.of(balance);
        }).valueUnits();
    }

    public static TransactionResult convert(
            ServerPlayer player,
            ResourceLocation conversionId,
            long amount,
            UUID operationId
    ) {
        if (amount < 1) {
            throw new IllegalArgumentException("Conversion amount must be positive");
        }
        CreatorDefinition definition = catalog().flatMap(value ->
                value.definition(DefinitionKinds.CONVERSION, conversionId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown conversion " + conversionId));
        ResourceLocation from = definition.id("from").orElseThrow();
        ResourceLocation to = definition.id("to").orElseThrow();
        if (from.equals(to)) {
            throw new IllegalArgumentException("Conversion currencies must differ");
        }
        long numerator = definition.integer("numerator").orElseThrow();
        long denominator = definition.integer("denominator").orElseThrow();
        long feeBasisPoints = definition.integer("fee_basis_points").orElse(0L);
        long limit = definition.integer("maximum_input").orElse(Long.MAX_VALUE);
        if (numerator < 1 || denominator < 1 || feeBasisPoints < 0 || feeBasisPoints > 10_000
                || amount > limit) {
            throw new IllegalArgumentException("Conversion definition or amount is invalid");
        }
        long output = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(numerator))
                .multiply(BigInteger.valueOf(10_000L - feeBasisPoints))
                .divide(BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(10_000L)))
                .longValueExact();
        if (output < 1) {
            throw new IllegalArgumentException("Conversion output rounds below one");
        }
        var transactions = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var revision = TransactionRuntime.currentDefinition().orElseThrow();
        var snapshot = transactions.service().snapshot(player.getUUID());
        SkillCatalog skills = PackRuntime.service().map(service -> SkillCatalog.from(
                service.live().snapshot().canonicalIr())).orElseThrow();
        var fromDefinition = skills.currency(from).orElseThrow(
                () -> new IllegalArgumentException("Unknown source currency " + from));
        var toDefinition = skills.currency(to).orElseThrow(
                () -> new IllegalArgumentException("Unknown target currency " + to));
        long fromAfter = Math.subtractExact(logicalBalance(snapshot, from, fromDefinition), amount);
        long toAfter = Math.addExact(logicalBalance(snapshot, to, toDefinition), output);
        TransactionStep step = new TransactionStep(
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "creator/conversion"),
                List.of(
                        logicalMutation(snapshot, from, fromDefinition, fromAfter),
                        logicalMutation(snapshot, to, toDefinition, toAfter)
                ), List.of(), List.of(), List.of());
        TransactionPlan plan = new TransactionPlan(
                player.getUUID(), player.getUUID(), new IdempotencyKey("creator.convert." + operationId),
                snapshot.stateRevision(), revision, ProgressionCause.GAMEPLAY,
                "Convert progression currency", step);
        return transactions.executeAndPersist(player, CascadePlan.single(plan), revision);
    }

    public static BuildShareCode.Build currentBuild(ServerPlayer player) {
        var transactions = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var snapshot = transactions.service().snapshot(player.getUUID());
        List<ResourceLocation> classes = ClassProgression.selectedClasses(snapshot).stream().toList();
        List<ResourceLocation> nodes = snapshot.paidCosts().keySet().stream()
                .filter(value -> value.ownerKind().equals(DefinitionKinds.TREE.id()))
                .map(value -> value.purchaseId()).distinct().toList();
        AbilityState abilities = AbilityRuntime.state(player);
        var slots = new LinkedHashMap<Integer, ResourceLocation>();
        abilities.assignments().forEach((slot, ability) -> slots.put(AbilityState.slotIndex(slot), ability));
        String digest = TransactionRuntime.currentDefinition().map(value -> value.semanticDigest()).orElse("0".repeat(64));
        return new BuildShareCode.Build(digest, classes, nodes, slots);
    }

    public static String saveLoadout(ServerPlayer player, ResourceLocation id) {
        BuildShareCode.Build build = currentBuild(player);
        String code = BuildShareCode.encode(build);
        CreatorProgressSavedData.get(player.getServer()).saveLoadout(player.getUUID(), id,
                new CreatorProgressSavedData.LoadoutState(build.definitionDigest(), code, Instant.now()));
        return code;
    }

    public static TransactionResult applyLoadout(
            ServerPlayer player,
            ResourceLocation loadoutId,
            UUID operationId
    ) {
        CreatorProgressSavedData.LoadoutState loadout = CreatorProgressSavedData.get(player.getServer())
                .loadout(player.getUUID(), loadoutId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown loadout " + loadoutId));
        return applyBuild(player, BuildShareCode.decode(loadout.buildCode()), operationId);
    }

    public static TransactionResult applyBuild(
            ServerPlayer player,
            BuildShareCode.Build build,
            UUID operationId
    ) {
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(operationId, "operationId");
        var transactions = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var revision = TransactionRuntime.currentDefinition().orElseThrow();
        if (!revision.semanticDigest().equals(build.definitionDigest())) {
            throw new IllegalStateException("Build uses a different definition digest");
        }
        var pack = PackRuntime.service().orElseThrow().live().snapshot().canonicalIr();
        SkillCatalog skills = SkillCatalog.from(pack);
        TreeCatalog trees = TreeCatalog.from(pack, skills);
        ClassCatalog classes = ClassCatalog.from(pack, skills, trees);
        AbilityCatalog abilities = AbilityCatalog.from(pack, skills, classes);
        ProgressionSnapshot initial = transactions.service().snapshot(player.getUUID());
        ProgressionSnapshot working = initial;
        var steps = new ArrayList<TransactionStep>();
        int planIndex = 0;

        var desiredClasses = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        desiredClasses.addAll(build.classes());
        var selectedClasses = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        selectedClasses.addAll(ClassProgression.selectedClasses(working));
        for (ResourceLocation selected : difference(selectedClasses, desiredClasses)) {
            var preview = ClassProgression.previewRespec(classes, skills, working, revision, selected);
            CascadePlan plan = ClassProgression.respec(
                    player.getUUID(), player.getUUID(), classes, skills, working, revision, selected,
                    preview.digest(), childKey(operationId, planIndex++), ProgressionCause.GAMEPLAY);
            working = appendPlan(steps, working, plan);
        }
        var missingClasses = difference(desiredClasses, ClassProgression.selectedClasses(working));
        while (!missingClasses.isEmpty()) {
            boolean progressed = false;
            String blocker = "Build class requirements cannot be satisfied";
            for (ResourceLocation missing : List.copyOf(missingClasses)) {
                var preview = ClassProgression.previewSelect(classes, skills, working, missing);
                if (!preview.allowed()) {
                    blocker = preview.blockers().getFirst();
                    continue;
                }
                CascadePlan plan = ClassProgression.select(
                        player.getUUID(), player.getUUID(), classes, skills, working, revision, missing,
                        childKey(operationId, planIndex++), ProgressionCause.GAMEPLAY);
                working = appendPlan(steps, working, plan);
                missingClasses.remove(missing);
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalStateException(blocker);
            }
        }

        var desiredNodes = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        desiredNodes.addAll(build.nodes());
        var ownedNodes = ownedTreeNodes(working);
        for (ResourceLocation extra : difference(ownedNodes, desiredNodes)) {
            Optional<TreeDefinition> tree = treeForNode(trees, extra);
            if (tree.isEmpty() || !ownedTreeNodes(working).contains(extra)) {
                continue;
            }
            var preview = TreeProgression.previewRefund(
                    trees, skills, working, revision, tree.orElseThrow().id(), extra);
            CascadePlan plan = TreeProgression.refund(
                    player.getUUID(), player.getUUID(), trees, skills, working, revision,
                    tree.orElseThrow().id(), extra, preview.digest(), childKey(operationId, planIndex++),
                    ProgressionCause.GAMEPLAY);
            working = appendPlan(steps, working, plan);
        }
        var missingNodes = difference(desiredNodes, ownedTreeNodes(working));
        while (!missingNodes.isEmpty()) {
            boolean progressed = false;
            String blocker = "Build tree requirements cannot be satisfied";
            for (ResourceLocation missing : List.copyOf(missingNodes)) {
                TreeDefinition tree = treeForNode(trees, missing).orElseThrow(
                        () -> new IllegalArgumentException("Unknown build tree node " + missing));
                var preview = TreeProgression.previewPurchase(trees, skills, working, tree.id(), missing);
                if (!preview.allowed()) {
                    blocker = preview.blockers().getFirst();
                    continue;
                }
                CascadePlan plan = TreeProgression.purchase(
                        player.getUUID(), player.getUUID(), trees, skills, working, revision,
                        tree.id(), missing, childKey(operationId, planIndex++), ProgressionCause.GAMEPLAY);
                working = appendPlan(steps, working, plan);
                missingNodes.remove(missing);
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalStateException(blocker);
            }
        }

        Map<ResourceLocation, ResourceLocation> assignments = AbilityProgression.state(
                abilities, working, player.level().getGameTime()).assignments();
        for (ResourceLocation slot : AbilityState.slots()) {
            ResourceLocation current = assignments.get(slot);
            ResourceLocation desired = build.abilities().get(AbilityState.slotIndex(slot));
            if (current != null && !current.equals(desired)) {
                CascadePlan plan = AbilityProgression.unassign(
                        player.getUUID(), player.getUUID(), working, revision, slot,
                        childKey(operationId, planIndex++), ProgressionCause.GAMEPLAY);
                working = appendPlan(steps, working, plan);
                assignments = AbilityProgression.state(
                        abilities, working, player.level().getGameTime()).assignments();
            }
        }
        for (var entry : build.abilities().entrySet()) {
            ResourceLocation slot = AbilityState.slotId(entry.getKey());
            if (!entry.getValue().equals(assignments.get(slot))) {
                CascadePlan plan = AbilityProgression.assign(
                        player.getUUID(), player.getUUID(), abilities, working, revision,
                        entry.getValue(), slot, childKey(operationId, planIndex++), ProgressionCause.GAMEPLAY);
                working = appendPlan(steps, working, plan);
                assignments = AbilityProgression.state(
                        abilities, working, player.level().getGameTime()).assignments();
            }
        }
        if (steps.isEmpty()) {
            throw new IllegalStateException("Build is already active");
        }
        TransactionPlan root = new TransactionPlan(
                player.getUUID(), player.getUUID(), new IdempotencyKey("creator.build." + operationId),
                initial.stateRevision(), revision, ProgressionCause.GAMEPLAY,
                "Apply creator build", steps.getFirst());
        CascadePlan combined = new CascadePlan(root, steps.subList(1, steps.size()));
        return transactions.executeAndPersist(player, combined, revision);
    }

    private static IdempotencyKey childKey(UUID operationId, int index) {
        return new IdempotencyKey("creator.build.plan." + operationId + "." + index);
    }

    private static ProgressionSnapshot appendPlan(
            List<TransactionStep> steps,
            ProgressionSnapshot snapshot,
            CascadePlan plan
    ) {
        steps.addAll(plan.steps());
        return ProgressionTransactionService.previewSnapshot(snapshot, plan);
    }

    private static TreeSet<ResourceLocation> difference(
            Collection<ResourceLocation> left,
            Collection<ResourceLocation> right
    ) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        result.addAll(left);
        result.removeAll(right);
        return result;
    }

    private static TreeSet<ResourceLocation> ownedTreeNodes(ProgressionSnapshot snapshot) {
        var result = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        snapshot.paidCosts().keySet().stream()
                .filter(value -> value.ownerKind().equals(DefinitionKinds.TREE.id()))
                .map(value -> value.purchaseId()).forEach(result::add);
        return result;
    }

    private static Optional<TreeDefinition> treeForNode(TreeCatalog catalog, ResourceLocation nodeId) {
        return catalog.trees().values().stream().filter(tree -> tree.nodesById().containsKey(nodeId)).findFirst();
    }

    public static CreatorProgressSavedData.ContractState assignContract(
            ServerPlayer player,
            ResourceLocation contractId,
            long epoch
    ) {
        CreatorDefinition definition = catalog().flatMap(value ->
                value.definition(DefinitionKinds.TRAINING_CONTRACT, contractId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown training contract " + contractId));
        List<ResourceLocation> eligible = definition.ids("eligible_skills");
        if (eligible.isEmpty()) {
            eligible = PackRuntime.service().map(service -> SkillCatalog.from(
                    service.live().snapshot().canonicalIr()).skills().keySet().stream().toList()).orElse(List.of());
        }
        return CreatorProgressSavedData.get(player.getServer()).assignContract(
                player.getUUID(), contractId, eligible,
                definition.integer("minimum_goal").orElse(10L),
                definition.integer("maximum_goal").orElse(100L), epoch);
    }

    public static long adjustResource(ServerPlayer player, ResourceLocation resourceId, long delta) {
        CreatorDefinition definition = catalog().flatMap(value ->
                value.definition(DefinitionKinds.RESOURCE, resourceId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown resource " + resourceId));
        long minimum = definition.integer("minimum").orElse(0L);
        long maximum = definition.integer("maximum").orElseThrow();
        long initial = definition.integer("initial").orElse(minimum);
        return CreatorProgressSavedData.get(player.getServer()).adjustResource(
                player.getUUID(), resourceId, delta, minimum, maximum, initial);
    }

    public static int prestige(ServerPlayer player, ResourceLocation prestigeId) {
        CreatorDefinition definition = catalog().flatMap(value ->
                value.definition(DefinitionKinds.PRESTIGE, prestigeId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown prestige track " + prestigeId));
        var transactions = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var snapshot = transactions.service().snapshot(player.getUUID());
        SkillCatalog skills = PackRuntime.service().map(service -> SkillCatalog.from(
                service.live().snapshot().canonicalIr())).orElseThrow();
        long totalLevel = skills.skills().values().stream()
                .mapToLong(skill -> SkillProgress.from(skill, snapshot).level()).sum();
        long required = definition.integer("minimum_total_level").orElse(0L);
        if (totalLevel < required) {
            throw new IllegalStateException("Prestige requires total level " + required);
        }
        CreatorProgressSavedData creatorData = CreatorProgressSavedData.get(player.getServer());
        CreatorProgressSavedData.PlayerState creatorState = creatorData.state(player.getUUID());
        int previous = creatorState.prestige().getOrDefault(prestigeId, 0);
        Optional<CreatorProgressSavedData.ResourceReward> resourceReward = definition.id("reward_resource")
                .map(resource -> {
                    CreatorDefinition resourceDefinition = catalog().flatMap(value ->
                            value.definition(DefinitionKinds.RESOURCE, resource)).orElseThrow(
                            () -> new IllegalArgumentException("Unknown prestige reward resource " + resource));
                    long minimum = resourceDefinition.integer("minimum").orElse(0L);
                    long maximum = resourceDefinition.integer("maximum").orElseThrow();
                    long initial = resourceDefinition.integer("initial").orElse(minimum);
                    long amount = definition.integer("reward_amount").orElse(1L);
                    long current = creatorState.resources().getOrDefault(resource, initial);
                    long next = Math.addExact(current, amount);
                    if (amount < 0 || next < minimum || next > maximum) {
                        throw new IllegalStateException("Prestige resource reward exceeds its bounds");
                    }
                    return new CreatorProgressSavedData.ResourceReward(
                            resource, amount, minimum, maximum, initial);
                });
        var desiredBalances = new java.util.TreeMap<ResourceLocation, Long>(
                ResourceLocation::compareNamespaced);
        for (ResourceLocation currency : definition.ids("reset_currencies")) {
            var currencyDefinition = skills.currency(currency).orElseThrow(
                    () -> new IllegalArgumentException("Unknown prestige reset currency " + currency));
            desiredBalances.put(currency, currencyDefinition.minimum());
        }
        definition.id("conversion_currency").ifPresent(currency -> {
            var currencyDefinition = skills.currency(currency).orElseThrow(
                    () -> new IllegalArgumentException("Unknown prestige conversion currency " + currency));
            long amount = Math.multiplyExact(
                    totalLevel, definition.integer("conversion_per_level").orElse(0L));
            if (amount > 0) {
                desiredBalances.put(currency, Math.addExact(
                        desiredBalances.getOrDefault(
                                currency, logicalBalance(snapshot, currency, currencyDefinition)), amount));
            }
        });
        definition.id("reward_currency").ifPresent(currency -> {
            var currencyDefinition = skills.currency(currency).orElseThrow(
                    () -> new IllegalArgumentException("Unknown prestige reward currency " + currency));
            long amount = definition.integer("reward_currency_amount").orElse(1L);
            if (amount < 1) {
                throw new IllegalArgumentException("Prestige reward currency amount is invalid");
            }
            desiredBalances.put(currency, Math.addExact(
                    desiredBalances.getOrDefault(
                            currency, logicalBalance(snapshot, currency, currencyDefinition)), amount));
        });
        var balanceMutations = new java.util.ArrayList<BalanceMutation>();
        desiredBalances.forEach((currency, desired) -> {
            CurrencyDefinition currencyDefinition = skills.currency(currency).orElseThrow();
            balanceMutations.add(logicalMutation(snapshot, currency, currencyDefinition, desired));
        });
        if (!balanceMutations.isEmpty()) {
            var revision = TransactionRuntime.currentDefinition().orElseThrow();
            TransactionStep step = new TransactionStep(
                    ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "creator/prestige"),
                    balanceMutations, List.of(), List.of(), List.of());
            TransactionPlan plan = new TransactionPlan(
                    player.getUUID(), player.getUUID(),
                    new IdempotencyKey("creator.prestige." + player.getUUID() + "." + prestigeId + "."
                            + Math.addExact(previous, 1)),
                    snapshot.stateRevision(), revision, ProgressionCause.GAMEPLAY,
                    "Commit creator prestige", step);
            TransactionResult result = transactions.executeAndPersist(
                    player, CascadePlan.single(plan), revision);
            if (!result.status().committed()) {
                throw new IllegalStateException("Prestige transaction failed. " + result.message());
            }
        }
        return creatorData.commitPrestige(
                player.getUUID(), prestigeId, previous, resourceReward);
    }

    public static void chooseMilestone(
            ServerPlayer player,
            ResourceLocation milestoneId,
            ResourceLocation choiceId
    ) {
        CreatorDefinition definition = catalog().flatMap(value ->
                value.definition(DefinitionKinds.MILESTONE_CHOICE, milestoneId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown milestone " + milestoneId));
        var choice = definition.list("choices").stream().map(value -> value.object())
                .filter(value -> value.get("id") != null
                        && value.get("id").text().filter(choiceId.toString()::equals).isPresent())
                .findFirst().orElseThrow(
                        () -> new IllegalArgumentException("Unknown milestone choice " + choiceId));
        ResourceLocation rewardBundle = choice.get("reward_bundle").text().map(ResourceLocation::parse)
                .orElseThrow(() -> new IllegalArgumentException("Milestone choice has no reward bundle"));
        CreatorProgressSavedData data = CreatorProgressSavedData.get(player.getServer());
        Optional<ResourceLocation> previous = Optional.ofNullable(
                data.state(player.getUUID()).milestoneChoices().get(milestoneId));
        if (previous.filter(choiceId::equals).isPresent()) {
            return;
        }
        boolean respecAllowed = definition.bool("respec_allowed", false);
        if (previous.isPresent() && !respecAllowed) {
            throw new IllegalStateException("Milestone choice cannot be changed");
        }
        String respecPolicy = definition.text("respec_policy").orElse("replace_without_reward");
        if (previous.isEmpty() || respecPolicy.equals("grant_each_choice")) {
            grantBundle(player, rewardBundle, new IdempotencyKey(
                    "creator.milestone." + player.getUUID() + "." + milestoneId + "." + choiceId));
        } else if (!respecPolicy.equals("replace_without_reward")) {
            throw new IllegalArgumentException("Milestone respec policy is invalid");
        }
        data.choose(player.getUUID(), milestoneId, choiceId, respecAllowed);
    }

    public static CreatorProgressSavedData.ComboState awardCombo(
            ServerPlayer player,
            ResourceLocation comboId,
            ResourceLocation award
    ) {
        CreatorDefinition definition = catalog().flatMap(value ->
                value.definition(DefinitionKinds.COMBO_MASTERY, comboId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown combo mastery " + comboId));
        List<ResourceLocation> sequence = definition.ids("sequence");
        long timeout = definition.integer("timeout_ticks").orElse(200L);
        return CreatorProgressSavedData.get(player.getServer()).awardCombo(
                player.getUUID(), comboId, sequence, award, player.level().getGameTime(), timeout,
                definition.integer("cooldown_ticks").orElse(20L),
                Math.toIntExact(definition.integer("mastery_cap_per_window").orElse(10L)),
                definition.integer("cap_window_ticks").orElse(1200L),
                Math.toIntExact(definition.integer("max_repeated_awards").orElse(4L)));
    }

    public static void onSkillAward(
            ServerPlayer player,
            UUID receiptId,
            ResourceLocation skillId,
            long awardedUnits
    ) {
        if (awardedUnits < 1) {
            return;
        }
        Optional<CreatorCatalog> available = catalog();
        if (available.isEmpty()) {
            return;
        }
        CreatorProgressSavedData data = CreatorProgressSavedData.get(player.getServer());
        if (!data.active()) {
            return;
        }
        long epoch = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        data.runSkillAward(player.getUUID(), receiptId, () -> {
            data.state(player.getUUID()).contracts().forEach((contractId, contract) -> {
                if (!contract.complete() && contract.epoch() == epoch && contract.skill().equals(skillId)) {
                    data.progressContract(player.getUUID(), contractId, skillId, 1L, epoch);
                }
            });
            available.orElseThrow().kind(DefinitionKinds.COMBO_MASTERY).forEach(combo -> {
                if (combo.ids("sequence").contains(skillId)) {
                    awardCombo(player, combo.key().id(), skillId);
                }
            });
            long wholeXp = awardedUnits / FixedPoint.SCALE;
            if (wholeXp > 0) {
                available.orElseThrow().kind(DefinitionKinds.CHALLENGE).forEach(challenge -> {
                    if (challenge.id("skill").filter(skillId::equals).isPresent()) {
                        long amount = Math.multiplyExact(
                                wholeXp, challenge.integer("progress_per_xp").orElse(1L));
                        data.progressChallenge(player.getUUID(), challenge.key().id(), amount,
                                challenge.integer("goal").orElseThrow());
                    }
                });
            }
        });
    }

    private static TransactionResult grantBundle(
            ServerPlayer player,
            ResourceLocation bundleId,
            IdempotencyKey idempotencyKey
    ) {
        CreatorDefinition bundle = catalog().flatMap(value ->
                value.definition(DefinitionKinds.GRANT_BUNDLE, bundleId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown grant bundle " + bundleId));
        SkillCatalog skills = PackRuntime.service().map(service -> SkillCatalog.from(
                service.live().snapshot().canonicalIr())).orElseThrow();
        var awards = new java.util.TreeMap<ResourceLocation, Long>(ResourceLocation::compareNamespaced);
        for (var entry : bundle.list("grants")) {
            Map<String, CreatorDefinition.CreatorValue> fields = entry.object();
            ResourceLocation currency = Optional.ofNullable(fields.get("currency"))
                    .flatMap(CreatorDefinition.CreatorValue::text).map(ResourceLocation::parse)
                    .orElseThrow(() -> new IllegalArgumentException("Grant bundle currency is unavailable"));
            long amount = Optional.ofNullable(fields.get("amount"))
                    .flatMap(value -> value.integer().isPresent()
                            ? Optional.of(value.integer().orElseThrow()) : Optional.empty())
                    .orElseThrow(() -> new IllegalArgumentException("Grant bundle amount is unavailable"));
            if (amount < 1) {
                throw new IllegalArgumentException("Grant bundle amount is invalid");
            }
            skills.currency(currency).orElseThrow(
                    () -> new IllegalArgumentException("Unknown grant bundle currency " + currency));
            awards.merge(currency, amount, Math::addExact);
        }
        if (awards.isEmpty() || awards.size() > 32) {
            throw new IllegalArgumentException("Grant bundle mutation count is invalid");
        }
        var transactions = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var revision = TransactionRuntime.currentDefinition().orElseThrow();
        var snapshot = transactions.service().snapshot(player.getUUID());
        var mutations = new java.util.ArrayList<BalanceMutation>();
        awards.forEach((currency, amount) -> {
            CurrencyDefinition currencyDefinition = skills.currency(currency).orElseThrow();
            long desired = Math.addExact(logicalBalance(snapshot, currency, currencyDefinition), amount);
            mutations.add(logicalMutation(snapshot, currency, currencyDefinition, desired));
        });
        TransactionStep step = new TransactionStep(
                ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "creator/grant_bundle"),
                mutations, List.of(), List.of(), List.of());
        TransactionPlan plan = new TransactionPlan(
                player.getUUID(), player.getUUID(), idempotencyKey,
                snapshot.stateRevision(), revision, ProgressionCause.GAMEPLAY,
                "Grant milestone reward bundle", step);
        TransactionResult result = transactions.executeAndPersist(
                player, CascadePlan.single(plan), revision);
        if (!result.status().committed()) {
            throw new IllegalStateException("Milestone reward transaction failed. " + result.message());
        }
        return result;
    }

    private static long logicalBalance(
            ProgressionSnapshot snapshot,
            ResourceLocation currency,
            CurrencyDefinition definition
    ) {
        return snapshot.balances().getOrDefault(currency, definition.initial());
    }

    private static BalanceMutation logicalMutation(
            ProgressionSnapshot snapshot,
            ResourceLocation currency,
            CurrencyDefinition definition,
            long desired
    ) {
        if (desired < definition.minimum() || desired > definition.maximum()) {
            throw new IllegalStateException("Creator currency change exceeds its bounds");
        }
        long stored = snapshot.balances().getOrDefault(currency, 0L);
        return new BalanceMutation(currency, Math.subtractExact(desired, stored),
                definition.minimum(), definition.maximum());
    }

    private record CachedCatalog(long generation, String digest, CreatorCatalog catalog) {
    }
}
