package com.envisione.progressiveskills.server.rule;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.rule.BlockOrigin;
import com.envisione.progressiveskills.common.rule.FakePlayerPolicy;
import com.envisione.progressiveskills.common.rule.RuleAntiExploitDecision;
import com.envisione.progressiveskills.common.rule.RuleAntiExploitEngine;
import com.envisione.progressiveskills.common.rule.RuleCatalog;
import com.envisione.progressiveskills.common.rule.RuleMemoryKeys;
import com.envisione.progressiveskills.common.rule.RuleStackResolver;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.skill.SkillRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class RuleRuntime {
    private static final int MAX_DEDUPE_TOKENS_PER_PLAYER_TICK = 256;
    private static final int MAX_PENDING_PISTONS = 512;
    private static final ProcessResult NO_ACTIVE_ROUTES = new ProcessResult(0, 0, "no active block routes");
    private static final ProcessResult NO_MATCH = new ProcessResult(0, 0, "no matcher accepted the block");
    private static final AtomicReference<RuntimeState> STATE = new AtomicReference<>();
    private static final Map<UUID, PlayerDedupe> DEDUPE = new HashMap<>();
    private static final Map<UUID, RuleTrace> LAST_TRACE = new ConcurrentHashMap<>();
    private static final Map<PistonKey, PistonPlan> PENDING_PISTONS = new HashMap<>();

    private RuleRuntime() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        PENDING_PISTONS.clear();
        reload(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockOrigin origin;
        if (event.getEntity() instanceof FakePlayer || !(event.getEntity() instanceof ServerPlayer player)) {
            origin = BlockOrigin.AUTOMATION_PLACED;
        } else {
            origin = player.gameMode.getGameModeForPlayer().isCreative()
                    ? BlockOrigin.CREATIVE_PLACED : BlockOrigin.SURVIVAL_PLACED;
        }
        BlockProvenanceSavedData store = BlockProvenanceSavedData.get(level.getServer());
        ResourceLocation dimension = level.dimension().location();
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            multiPlace.getReplacedBlockSnapshots().forEach(snapshot ->
                    store.recordPlacement(dimension, snapshot.getPos(), origin));
        } else {
            store.recordPlacement(dimension, event.getPos(), origin);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long gameTick = level.getGameTime();
        PENDING_PISTONS.keySet().removeIf(key -> key.gameTick() + 2 < gameTick);
        BlockProvenanceSavedData store = BlockProvenanceSavedData.get(level.getServer());
        if (PENDING_PISTONS.size() >= MAX_PENDING_PISTONS) {
            PENDING_PISTONS.clear();
            store.markUnreliable("Piston provenance capacity is full");
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        PistonKey key = pistonKey(level, event, gameTick);
        PENDING_PISTONS.put(key, new PistonPlan(
                List.copyOf(resolver.getToPush()),
                List.copyOf(resolver.getToDestroy()),
                resolver.getPushDirection()
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPistonPost(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockProvenanceSavedData store = BlockProvenanceSavedData.get(level.getServer());
        PistonPlan plan = PENDING_PISTONS.remove(pistonKey(level, event, level.getGameTime()));
        if (plan == null) {
            store.markUnreliable("Piston provenance plan is missing");
            return;
        }
        store.move(level.dimension().location(), plan.sources(), plan.destroyed(), plan.direction());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            BlockOrigin origin = BlockProvenanceSavedData.get(level.getServer())
                    .consume(level.dimension().location(), event.getPos());
            processBlockBreak(
                    player,
                    event.getState(),
                    event.getPos(),
                    level.dimension().location(),
                    level.getGameTime(),
                    origin
            );
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DEDUPE.remove(player.getUUID());
            LAST_TRACE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        pause();
        DEDUPE.clear();
        LAST_TRACE.clear();
        PENDING_PISTONS.clear();
    }

    public static void pause() {
        STATE.set(null);
    }

    public static void reload(MinecraftServer server) {
        var service = PackRuntime.service().orElseThrow(
                () -> new IllegalStateException("Pack runtime is unavailable")
        );
        var live = service.live();
        SkillCatalog skills = SkillCatalog.from(live.snapshot().canonicalIr());
        RuleCatalog rules = RuleCatalog.from(live.snapshot().canonicalIr(), skills);
        var memory = new LinkedHashMap<ResourceLocation, RuleMemoryKeys>();
        rules.rules().forEach((id, rule) -> memory.put(id, RuleMemoryKeys.forRule(id)));
        STATE.set(new RuntimeState(
                server,
                new DefinitionRevision(live.generation(), live.snapshot().contentDigest()),
                skills,
                rules,
                BlockRuleTable.compile(rules),
                Map.copyOf(memory)
        ));
        DEDUPE.clear();
    }

    public static Optional<RuleCatalog> catalog() {
        RuntimeState state = STATE.get();
        return state == null ? Optional.empty() : Optional.of(state.rules());
    }

    public static Optional<RuleTrace> lastTrace(UUID playerId) {
        return Optional.ofNullable(LAST_TRACE.get(playerId));
    }

    public static ProcessResult processBlockBreak(
            ServerPlayer player,
            BlockState blockState,
            BlockPos position,
            ResourceLocation dimension,
            long gameTick
    ) {
        return processBlockBreak(player, blockState, position, dimension, gameTick, BlockOrigin.NATURAL);
    }

    public static ProcessResult processBlockBreak(
            ServerPlayer player,
            BlockState blockState,
            BlockPos position,
            ResourceLocation dimension,
            long gameTick,
            BlockOrigin origin
    ) {
        RuntimeState state = STATE.get();
        if (state == null || state.server() != player.getServer() || state.blockRules().empty()) {
            return NO_ACTIVE_ROUTES;
        }
        List<BlockRuleTable.CompiledRule> matched = state.blockRules().match(blockState);
        if (matched.isEmpty()) {
            return NO_MATCH;
        }
        if (!(player instanceof FakePlayer)) {
            var data = player.getData(PsDataAttachments.PLAYER_DATA);
            if (!data.active() || !data.view().stateDefinition().filter(state.definition()::equals).isPresent()) {
                RuleTrace trace = new RuleTrace(
                        "progressiveskills:block_break",
                        BuiltinBlockIds.id(blockState).toString(),
                        origin,
                        matched.size(),
                        0,
                        0,
                        0,
                        "player progression state is not reconciled",
                        ""
                );
                LAST_TRACE.put(player.getUUID(), trace);
                return ProcessResult.empty(trace.outcome());
            }
        }
        long token = token(position, dimension, gameTick);
        DedupeResult dedupe = claim(player.getUUID(), gameTick, token);
        if (dedupe != DedupeResult.ACCEPTED) {
            RuleTrace trace = new RuleTrace(
                    "progressiveskills:block_break",
                    BuiltinBlockIds.id(blockState).toString(),
                    origin,
                    matched.size(),
                    0,
                    0,
                    0,
                    dedupe == DedupeResult.DUPLICATE ? "duplicate event rejected" : "dedupe capacity exhausted",
                    ""
            );
            LAST_TRACE.put(player.getUUID(), trace);
            return ProcessResult.empty(trace.outcome());
        }

        var context = TransactionRuntime.context(player.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable")
        );
        var snapshot = context.service().snapshot(player.getUUID());
        var eligible = new ArrayList<RuleStackResolver.Candidate>();
        String lastRejection = "no eligible route";
        for (BlockRuleTable.CompiledRule compiled : matched) {
            var rule = compiled.rule();
            if (!rule.antiExploit().allows(origin)) {
                lastRejection = "block origin " + origin.serializedName() + " is not allowed";
                continue;
            }
            if (player instanceof FakePlayer && rule.antiExploit().fakePlayers() == FakePlayerPolicy.DENY) {
                lastRejection = "fake player policy denied the route";
                continue;
            }
            Optional<String> rejection = RuleAntiExploitEngine.rejectionReason(
                    rule,
                    state.memory().get(rule.id()),
                    snapshot,
                    gameTick
            );
            if (rejection.isPresent()) {
                lastRejection = rejection.orElseThrow();
                continue;
            }
            eligible.add(new RuleStackResolver.Candidate(rule, compiled.multipliedBaseUnits()));
        }
        List<RuleStackResolver.Candidate> selected = RuleStackResolver.resolve(eligible);
        long awarded = 0;
        String transaction = "";
        int committed = 0;
        for (int index = 0; index < selected.size(); index++) {
            RuleStackResolver.Candidate candidate = selected.get(index);
            var current = context.service().snapshot(player.getUUID());
            RuleAntiExploitDecision decision = RuleAntiExploitEngine.evaluate(
                    candidate.rule(),
                    state.memory().get(candidate.rule().id()),
                    current,
                    gameTick,
                    candidate.amountUnits()
            );
            if (!decision.accepted()) {
                lastRejection = decision.reason();
                continue;
            }
            var skill = state.skills().skill(candidate.rule().output().skill()).orElseThrow();
            var result = SkillRuntime.awardRule(
                    player,
                    skill,
                    decision.awardedUnits(),
                    candidate.rule().id(),
                    idempotency(state.definition(), player.getUUID(), token, index),
                    decision.memoryMutations()
            );
            if (result.transaction().status().committed()) {
                awarded = Math.addExact(awarded, decision.awardedUnits());
                committed++;
                transaction = result.transaction().transactionId().toString();
                lastRejection = "committed";
            } else {
                lastRejection = result.transaction().message();
            }
        }
        RuleTrace trace = new RuleTrace(
                "progressiveskills:block_break",
                BuiltinBlockIds.id(blockState).toString(),
                origin,
                matched.size(),
                eligible.size(),
                selected.size(),
                awarded,
                lastRejection,
                transaction
        );
        LAST_TRACE.put(player.getUUID(), trace);
        return new ProcessResult(committed, awarded, trace.outcome());
    }

    private static DedupeResult claim(UUID playerId, long tick, long token) {
        PlayerDedupe state = DEDUPE.computeIfAbsent(playerId, ignored -> new PlayerDedupe());
        if (state.tick != tick) {
            state.tick = tick;
            state.tokens.clear();
        }
        if (state.tokens.contains(token)) {
            return DedupeResult.DUPLICATE;
        }
        if (state.tokens.size() >= MAX_DEDUPE_TOKENS_PER_PLAYER_TICK) {
            return DedupeResult.FULL;
        }
        state.tokens.add(token);
        return DedupeResult.ACCEPTED;
    }

    private static long token(BlockPos position, ResourceLocation dimension, long tick) {
        long value = position.asLong() ^ Long.rotateLeft(tick, 21) ^ Integer.toUnsignedLong(dimension.hashCode());
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return value;
    }

    private static String idempotency(
            DefinitionRevision definition,
            UUID player,
            long token,
            int routeIndex
    ) {
        return "phase8/event/" + definition.generation() + "/" + player + "/"
                + Long.toUnsignedString(token) + "/" + routeIndex;
    }

    private static PistonKey pistonKey(ServerLevel level, PistonEvent event, long gameTick) {
        return new PistonKey(
                level.dimension().location(),
                event.getPos().asLong(),
                event.getDirection(),
                event.getPistonMoveType(),
                gameTick
        );
    }

    public record RuleTrace(
            String trigger,
            String subject,
            BlockOrigin origin,
            int candidates,
            int eligible,
            int selected,
            long awardedUnits,
            String outcome,
            String transactionId
    ) {
        public String awarded() {
            return FixedPoint.format(awardedUnits);
        }
    }

    public record ProcessResult(int committedRules, long awardedUnits, String outcome) {
        static ProcessResult empty(String outcome) {
            return new ProcessResult(0, 0, outcome);
        }
    }

    private record RuntimeState(
            MinecraftServer server,
            DefinitionRevision definition,
            SkillCatalog skills,
            RuleCatalog rules,
            BlockRuleTable blockRules,
            Map<ResourceLocation, RuleMemoryKeys> memory
    ) {
    }

    private static final class PlayerDedupe {
        private long tick = Long.MIN_VALUE;
        private final HashSet<Long> tokens = new HashSet<>();
    }

    private enum DedupeResult {
        ACCEPTED,
        DUPLICATE,
        FULL
    }

    private record PistonKey(
            ResourceLocation dimension,
            long position,
            Direction direction,
            PistonEvent.PistonMoveType moveType,
            long gameTick
    ) {
    }

    private record PistonPlan(List<BlockPos> sources, List<BlockPos> destroyed, Direction direction) {
    }

    private static final class BuiltinBlockIds {
        private BuiltinBlockIds() {
        }

        static ResourceLocation id(BlockState state) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        }
    }
}
