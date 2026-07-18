package com.envisione.progressiveskills.common.hardening;

import com.envisione.progressiveskills.common.pack.TomlDocument;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class PerformanceWorkload {
    private static final Map<Contract, RulePerformanceDriver> DRIVERS = new ConcurrentHashMap<>();

    private PerformanceWorkload() {
    }

    public static Contract readContract(Path path) throws IOException {
        Map<String, Object> root = TomlDocument.read(path).values();
        Map<String, Object> pack = object(root, "pack");
        Map<String, Object> timing = object(root, "timing");
        Map<String, Object> traffic = object(root, "traffic");
        Map<String, Object> actions = object(root, "actions");
        return new Contract(
                integer(pack, "definition_count"),
                integer(pack, "routed_rule_count"),
                integer(pack, "routes_per_definition"),
                integer(timing, "warmup_ticks"),
                integer(timing, "capture_ticks"),
                integer(traffic, "selection_cycle_events"),
                integer(traffic, "no_match_events"),
                integer(traffic, "single_match_events"),
                integer(traffic, "four_match_events"),
                integer(actions, "mining_period_ticks"),
                integer(actions, "combat_period_ticks"),
                integer(actions, "movement_sample_period_ticks"),
                integer(actions, "condition_period_ticks"),
                longValue(root, "pack_seed"),
                longValue(root, "player_script_seed")
        );
    }

    public static Contract reference() {
        return new Contract(
                10_000, 50_000, 5, 18_000, 36_000,
                20, 14, 5, 1, 4, 10, 1, 20,
                1_393_753_992_385_309_920L, 1_084_818_905_618_843_912L);
    }

    public static Path generateFixtureIndex(Contract contract, Path output) throws IOException {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(output, "output");
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (var data = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)))) {
            data.writeInt(1);
            data.writeInt(contract.definitionCount());
            data.writeInt(contract.routedRuleCount());
            long state = contract.packSeed();
            for (int definition = 0; definition < contract.definitionCount(); definition++) {
                data.writeInt(definition);
                for (int route = 0; route < contract.routesPerDefinition(); route++) {
                    state = mix(state + definition * 31L + route);
                    data.writeLong(state);
                }
            }
        }
        return output;
    }

    public static Result run(Contract contract, int players, int ticks) {
        Objects.requireNonNull(contract, "contract");
        if (players < 1 || players > 100 || ticks < 1 || ticks > contract.captureTicks()) {
            throw new IllegalArgumentException("Performance workload size is invalid");
        }
        RulePerformanceDriver driver = DRIVERS.computeIfAbsent(contract, RulePerformanceDriver::compile);
        long[] tickNanos = new long[ticks];
        long events = 0;
        long matchedRoutes = 0;
        long checksum = contract.playerScriptSeed();
        long ordinal = 0;
        for (int tick = 0; tick < ticks; tick++) {
            long started = System.nanoTime();
            for (int player = 0; player < players; player++) {
                int role = role(player, players);
                int period = switch (role) {
                    case 0 -> contract.miningPeriodTicks();
                    case 1 -> contract.combatPeriodTicks();
                    case 2 -> contract.movementPeriodTicks();
                    default -> contract.conditionPeriodTicks();
                };
                if (Math.floorMod(tick + player, period) != 0) {
                    continue;
                }
                int selection = Math.floorMod(ordinal++, contract.selectionCycleEvents());
                int matches = selection < contract.noMatchEvents() ? 0
                        : selection < contract.noMatchEvents() + contract.singleMatchEvents() ? 1
                        : contract.fourMatchEvents() > 0 ? 4 : 0;
                events = Math.addExact(events, 1L);
                matchedRoutes = Math.addExact(matchedRoutes, matches);
                long event = mix(contract.packSeed() ^ tick * 131L ^ player * 17L ^ role);
                var resolved = driver.resolve(matches, event);
                matchedRoutes = Math.addExact(matchedRoutes, resolved.size() - matches);
                for (var candidate : resolved) {
                    checksum = mix(checksum ^ event ^ candidate.rule().id().hashCode()
                            ^ candidate.amountUnits());
                }
            }
            tickNanos[tick] = System.nanoTime() - started;
        }
        long[] sorted = tickNanos.clone();
        Arrays.sort(sorted);
        return new Result(players, ticks, events, matchedRoutes, checksum, driver.routeCount(),
                percentile(sorted, 95), percentile(sorted, 99), Arrays.stream(tickNanos).max().orElse(0L));
    }

    public static Result runReference(Contract contract, int players) {
        run(contract, players, contract.warmupTicks());
        return run(contract, players, contract.captureTicks());
    }

    private static int role(int player, int players) {
        int percentile = player * 100 / players;
        return percentile < 40 ? 0 : percentile < 70 ? 1 : percentile < 90 ? 2 : 3;
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = Math.min(sorted.length - 1,
                Math.max(0, (int) Math.ceil(sorted.length * percentile / 100.0D) - 1));
        return sorted[index];
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Performance contract section is unavailable " + name);
        }
        return (Map<String, Object>) value;
    }

    private static int integer(Map<String, Object> values, String name) {
        return Math.toIntExact(longValue(values, name));
    }

    private static long longValue(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Performance contract value is unavailable " + name);
        }
        return number.longValue();
    }

    public record Contract(
            int definitionCount,
            int routedRuleCount,
            int routesPerDefinition,
            int warmupTicks,
            int captureTicks,
            int selectionCycleEvents,
            int noMatchEvents,
            int singleMatchEvents,
            int fourMatchEvents,
            int miningPeriodTicks,
            int combatPeriodTicks,
            int movementPeriodTicks,
            int conditionPeriodTicks,
            long packSeed,
            long playerScriptSeed
    ) {
        public Contract {
            if (definitionCount < 1 || (long) routedRuleCount != (long) definitionCount * routesPerDefinition
                    || routesPerDefinition < 1 || warmupTicks < 0 || captureTicks < 1
                    || selectionCycleEvents != noMatchEvents + singleMatchEvents + fourMatchEvents
                    || noMatchEvents < 0 || singleMatchEvents < 0 || fourMatchEvents < 0
                    || miningPeriodTicks < 1 || combatPeriodTicks < 1
                    || movementPeriodTicks < 1 || conditionPeriodTicks < 1) {
                throw new IllegalArgumentException("Performance contract is inconsistent");
            }
        }
    }

    public record Result(
            int players,
            int ticks,
            long events,
            long matchedRoutes,
            long checksum,
            int compiledRoutes,
            long p95TickNanos,
            long p99TickNanos,
            long maximumTickNanos
    ) {
        public boolean withinReferenceBudget() {
            return players == 40
                    ? p95TickNanos <= 750_000L && p99TickNanos <= 1_500_000L
                    : players == 100 && p95TickNanos <= 2_000_000L && p99TickNanos <= 4_000_000L;
        }
    }
}
