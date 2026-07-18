package com.envisione.progressiveskills.server.social;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.social.SharedAwardAllocator;
import com.envisione.progressiveskills.common.skill.SkillProgress;
import com.envisione.progressiveskills.server.hardening.DecisionTraceRuntime;
import com.envisione.progressiveskills.server.skill.SkillRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class CombatContributionRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(
            ProjectIdentity.MOD_ID, "combat_assist");
    private static final int MAX_VICTIMS = 4_096;
    private static final int MAX_CONTRIBUTORS = 32;
    private static final Map<UUID, DamageWindow> WINDOWS = new LinkedHashMap<>();

    private CombatContributionRuntime() {
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker.getUUID().equals(event.getEntity().getUUID())
                || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() <= 0.0F) {
            return;
        }
        long damageMilli = Math.max(1L, Math.round(event.getNewDamage() * 1_000.0D));
        recordDamage(event.getEntity().getUUID(), attacker.getUUID(), damageMilli, level.getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        DamageWindow window = removeWindow(event.getEntity().getUUID());
        if (window == null) {
            return;
        }
        try {
            award(level, event.getEntity().getUUID(), event.getEntity() instanceof ServerPlayer,
                    level.getGameTime(), window,
                    event.getSource().getEntity() instanceof ServerPlayer player
                            ? Optional.of(player.getUUID()) : Optional.empty());
        } catch (RuntimeException exception) {
            LOGGER.error("[ProgressiveSkills] combat contribution award failed", exception);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        synchronized (CombatContributionRuntime.class) {
            WINDOWS.clear();
        }
    }

    private static void award(
            ServerLevel level,
            UUID victim,
            boolean pvp,
            long gameTime,
            DamageWindow window,
            Optional<UUID> killer
    ) {
        var configured = CombatContributionProfiles.active().orElse(null);
        if (configured == null) {
            return;
        }
        var skill = SkillRuntime.catalog().flatMap(catalog -> catalog.skill(configured.skill()))
                .filter(value -> value.enabled()).orElse(null);
        if (skill == null) {
            return;
        }
        var parties = SocialProviderRuntime.parties(level.getServer());
        var eligible = new ArrayList<DamageEntry>();
        window.damage().forEach((player, damage) -> {
            if (gameTime >= damage.lastGameTick()
                    && gameTime - damage.lastGameTick() <= configured.policy().assistWindowTicks()
                    && damage.damageMilli() >= configured.policy().minimumDamageMilli()
                    && level.getServer().getPlayerList().getPlayer(player) != null) {
                eligible.add(new DamageEntry(player, damage.damageMilli()));
            }
        });
        if (eligible.isEmpty()) {
            return;
        }
        eligible.sort(Comparator.comparingLong(DamageEntry::damageMilli).reversed()
                .thenComparing(DamageEntry::player));
        UUID actorId = killer.filter(id -> parties.party(id).isPresent()).orElseGet(() ->
                eligible.stream().map(DamageEntry::player)
                        .filter(id -> parties.party(id).isPresent()).findFirst().orElse(null));
        if (actorId == null) {
            return;
        }
        var party = parties.party(actorId).orElseThrow();
        var weights = new LinkedHashMap<UUID, Long>();
        eligible.stream().filter(entry -> party.members().contains(entry.player()))
                .forEach(entry -> weights.put(entry.player(), entry.damageMilli()));
        if (weights.isEmpty()) {
            return;
        }
        long totalDamage = weights.values().stream().reduce(0L, Math::addExact);
        long requested = configured.policy().awardForDamage(totalDamage);
        if (requested < 1) {
            return;
        }
        ServerPlayer actor = level.getServer().getPlayerList().getPlayer(actorId);
        if (actor == null) {
            return;
        }
        if (!pvp) {
            PartyContributionService.award(actor, SOURCE, skill, requested, weights,
                    "Combat assist damage share with mentor catchup");
            return;
        }
        var baseShares = SharedAwardAllocator.allocate(requested, weights);
        var allowedShares = new LinkedHashMap<UUID, Long>();
        var transactions = TransactionRuntime.context(level.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable"));
        var ledger = PvpAwardLedgerSavedData.get(level.getServer());
        long now = System.currentTimeMillis();
        long epochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        int victimLevel = SkillProgress.from(skill, transactions.service().snapshot(victim)).level();
        baseShares.forEach((contributor, share) -> {
            int attackerLevel = SkillProgress.from(
                    skill, transactions.service().snapshot(contributor)).level();
            var limited = ledger.limit(contributor, victim, now, epochDay, share,
                    attackerLevel, victimLevel, configured.policy());
            if (limited.allowedUnits() > 0) {
                allowedShares.put(contributor, limited.allowedUnits());
            }
            long revision = transactions.service().snapshot(contributor).stateRevision();
            String reason = "Pvp share allowed " + limited.allowedUnits()
                    + " denied " + limited.deniedUnits()
                    + " repeated " + limited.repeated()
                    + " capped " + limited.capped();
            DecisionTraceRuntime.record(contributor, "pvp_assist", victim.toString(),
                    limited.allowedUnits() > 0, reason, revision);
        });
        if (!allowedShares.isEmpty()) {
            PartyContributionService.awardExact(actor, SOURCE, skill, allowedShares,
                    "Pvp assist share after pair cooldown daily cap and level scaling");
        }
    }

    private static synchronized void recordDamage(
            UUID victim,
            UUID attacker,
            long damageMilli,
            long gameTick
    ) {
        DamageWindow window = WINDOWS.get(victim);
        if (window == null) {
            if (WINDOWS.size() >= MAX_VICTIMS) {
                WINDOWS.remove(WINDOWS.keySet().iterator().next());
            }
            window = new DamageWindow(new LinkedHashMap<>());
            WINDOWS.put(victim, window);
        }
        Damage previous = window.damage().get(attacker);
        if (previous == null && window.damage().size() >= MAX_CONTRIBUTORS) {
            UUID oldest = window.damage().entrySet().stream()
                    .min(Map.Entry.comparingByValue(Comparator.comparingLong(Damage::lastGameTick)))
                    .orElseThrow().getKey();
            window.damage().remove(oldest);
        }
        long next = previous == null ? damageMilli : Math.addExact(previous.damageMilli(), damageMilli);
        window.damage().put(attacker, new Damage(next, gameTick));
    }

    private static synchronized DamageWindow removeWindow(UUID victim) {
        return WINDOWS.remove(victim);
    }

    private record DamageWindow(Map<UUID, Damage> damage) {
    }

    private record Damage(long damageMilli, long lastGameTick) {
    }

    private record DamageEntry(UUID player, long damageMilli) {
    }
}
