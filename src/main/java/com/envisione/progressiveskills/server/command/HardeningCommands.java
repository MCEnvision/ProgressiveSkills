package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.hardening.ReproductionBundle;
import com.envisione.progressiveskills.common.hardening.PerformanceWorkload;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.server.carrier.BehaviorArchiveSavedData;
import com.envisione.progressiveskills.server.hardening.DecisionTraceRuntime;
import com.envisione.progressiveskills.server.hardening.HardeningRuntime;
import com.envisione.progressiveskills.server.hardening.CapturedDecisionReplayExecutor;
import com.envisione.progressiveskills.server.hardening.MassCheckSavedData;
import com.envisione.progressiveskills.server.hardening.ReproductionReplayService;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.provider.ProviderRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class HardeningCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private HardeningCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("doctor")
                        .executes(context -> doctor(context.getSource(), false))
                        .then(Commands.literal("json").executes(context -> doctor(context.getSource(), true))))
                .then(Commands.literal("why")
                        .then(Commands.literal("latest").executes(context -> why(context.getSource(), false)))
                        .then(Commands.literal("verbose").executes(context -> why(context.getSource(), true))))
                .then(Commands.literal("check")
                        .then(Commands.literal("start").executes(context -> checkStart(context.getSource())))
                        .then(Commands.literal("next").executes(context -> checkNext(context.getSource())))
                        .then(markCommand("pass", MassCheckSavedData.Result.PASS))
                        .then(markCommand("fail", MassCheckSavedData.Result.FAIL))
                        .then(markCommand("skip", MassCheckSavedData.Result.SKIP))
                        .then(Commands.literal("status").executes(context -> checkStatus(context.getSource())))
                        .then(Commands.literal("finish").executes(context -> checkFinish(context.getSource())))
                        .then(Commands.literal("export").executes(context -> checkExport(context.getSource()))))
                .then(Commands.literal("reproduce")
                        .then(Commands.literal("export").executes(context -> reproduction(context.getSource())))
                        .then(Commands.literal("replay")
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .executes(context -> replay(context.getSource(),
                                                StringArgumentType.getString(context, "file"))))))
                .then(Commands.literal("perf").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("smoke")
                                .executes(context -> performanceSmoke(context.getSource())))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> markCommand(
            String name,
            MassCheckSavedData.Result result
    ) {
        return Commands.literal(name)
                .executes(context -> checkMark(context.getSource(), result, ""))
                .then(Commands.argument("note", StringArgumentType.greedyString())
                        .executes(context -> checkMark(context.getSource(), result,
                                StringArgumentType.getString(context, "note"))));
    }

    private static int doctor(CommandSourceStack source, boolean json) {
        var checks = new LinkedHashMap<String, String>();
        checks.put("pack", PackRuntime.service().map(value -> "healthy generation " + value.live().generation())
                .orElse("unavailable"));
        checks.put("transactions", TransactionRuntime.context(source.getServer()).isPresent() ? "healthy" : "unavailable");
        var archive = BehaviorArchiveSavedData.get(source.getServer());
        var verification = archive.verify();
        checks.put("carrier_archive", verification.valid() ? "healthy entries " + verification.checkedEntries()
                : "invalid " + verification.issue().orElse("unknown"));
        checks.put("providers", ProviderRuntime.registry().map(value -> value.probeAll().toString())
                .orElse("unavailable"));
        checks.put("performance", HardeningRuntime.performance().snapshots().toString());
        if (source.getEntity() instanceof ServerPlayer player) {
            checks.put("network", PsNetworking.serverStatus(player.getUUID()).map(Object::toString).orElse("no session"));
            checks.put("player_data", TransactionRuntime.context(source.getServer())
                    .map(value -> value.service().snapshot(player.getUUID()).toString()).orElse("unavailable"));
        }
        if (json) {
            success(source, json(checks));
        } else {
            checks.forEach((name, value) -> success(source, name + ". " + value + "."));
        }
        return checks.values().stream().allMatch(value -> !value.startsWith("unavailable")
                && !value.startsWith("invalid")) ? 1 : 0;
    }

    private static int why(CommandSourceStack source, boolean verbose) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var latest = DecisionTraceRuntime.latest(player.getUUID());
            if (latest.isEmpty()) {
                failure(source, "No progression decision has been recorded for this session.");
                return 0;
            }
            var value = latest.orElseThrow();
            success(source, value.category() + ". " + value.subject() + ". "
                    + (value.allowed() ? "Allowed. " : "Denied. ") + value.reason() + ".");
            if (verbose) {
                success(source, "At " + value.at() + ". State revision " + value.stateRevision() + ".");
                DecisionTraceRuntime.history(player.getUUID()).forEach(history -> success(source,
                        history.at() + ". " + history.category() + ". " + history.reason() + "."));
            }
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            failure(source, "A player must run this command.");
            return 0;
        }
    }

    private static int checkStart(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MassCheckSavedData.Session session = MassCheckSavedData.get(source.getServer()).start(player.getUUID());
            return showCheck(source, session);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            failure(source, "A player must run the mass check.");
            return 0;
        }
    }

    private static int checkNext(CommandSourceStack source) {
        return withSession(source, data -> showCheck(source,
                data.next(source.getPlayerOrException().getUUID())));
    }

    private static int checkMark(
            CommandSourceStack source,
            MassCheckSavedData.Result result,
            String note
    ) {
        return withSession(source, data -> showCheck(source,
                data.mark(source.getPlayerOrException().getUUID(), result, note)));
    }

    private static int checkStatus(CommandSourceStack source) {
        return withSession(source, data -> {
            var session = data.session(source.getPlayerOrException().getUUID()).orElseThrow();
            success(source, "Check " + (session.index() + 1) + " of " + MassCheckSavedData.CHECKS.size()
                    + ". Passed " + session.passed() + ". Failed " + session.failed()
                    + ". Finished " + session.finished() + ".");
            return showCheck(source, session);
        });
    }

    private static int checkFinish(CommandSourceStack source) {
        return withSession(source, data -> {
            var session = data.finish(source.getPlayerOrException().getUUID());
            success(source, "Mass check finished. Passed " + session.passed() + ". Failed "
                    + session.failed() + ". Pending "
                    + java.util.Collections.frequency(session.results(), MassCheckSavedData.Result.PENDING) + ".");
            return session.failed() == 0 ? 1 : 0;
        });
    }

    private static int checkExport(CommandSourceStack source) {
        return withSession(source, data -> {
            ServerPlayer player = source.getPlayerOrException();
            var session = data.session(player.getUUID()).orElseThrow();
            var lines = new ArrayList<String>();
            lines.add("ProgressiveSkills mass check");
            lines.add("Player " + player.getGameProfile().getName());
            lines.add("Started " + session.startedAt());
            lines.add("Updated " + session.updatedAt());
            for (int index = 0; index < MassCheckSavedData.CHECKS.size(); index++) {
                lines.add((index + 1) + ". " + session.results().get(index) + ". "
                        + MassCheckSavedData.CHECKS.get(index) + " " + session.notes().get(index));
            }
            Path report = reports(source).resolve("mass_check_" + player.getUUID() + ".txt");
            write(report, String.join(System.lineSeparator(), lines));
            success(source, "Mass check exported to " + report + ".");
            return 1;
        });
    }

    private static int reproduction(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var context = TransactionRuntime.context(source.getServer()).orElseThrow(
                    () -> new IllegalStateException("Transaction runtime is unavailable"));
            var state = context.service().snapshot(player.getUUID());
            var definition = TransactionRuntime.currentDefinition().orElseThrow(
                    () -> new IllegalStateException("Definition revision is unavailable"));
            var redacted = new LinkedHashMap<String, Long>();
            state.balances().forEach((id, amount) -> redacted.put("balance." + id, amount));
            redacted.put("ownership_count", (long) state.ownership().size());
            redacted.put("paid_cost_count", (long) state.paidCosts().size());
            var events = new ArrayList<ReproductionBundle.Event>();
            var history = DecisionTraceRuntime.history(player.getUUID());
            CapturedDecisionReplayExecutor.capture(redacted, player, history);
            for (int index = 0; index < history.size(); index++) {
                var value = history.get(index);
                events.add(new ReproductionBundle.Event(
                        index, value.category(), value.subject(), value.allowed() ? 1 : 0,
                        value.stateRevision()));
            }
            ReproductionBundle bundle = new ReproductionBundle(
                    2, UUID.randomUUID(), Instant.now(), definition.semanticDigest(), definition.generation(),
                    state.stateRevision(), player.getUUID().getMostSignificantBits(), redacted, events);
            Path report = reports(source).resolve("reproduction_" + bundle.bundleId() + ".json");
            write(report, reproductionJson(bundle));
            success(source, "Reproduction bundle exported to " + report + ".");
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException
                 | IllegalArgumentException | IllegalStateException | IOException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int replay(CommandSourceStack source, String file) {
        try {
            if (!file.matches("reproduction_[a-zA-Z0-9-]+\\.json")) {
                throw new IllegalArgumentException("Reproduction filename is invalid");
            }
            Path directory = reports(source);
            Path target = directory.resolve(file).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalArgumentException("Reproduction path escapes its root");
            }
            String definitionDigest = TransactionRuntime.currentDefinition().orElseThrow(
                    () -> new IllegalStateException("Definition revision is unavailable")).semanticDigest();
            var result = ReproductionReplayService.replay(
                    target, definitionDigest, new CapturedDecisionReplayExecutor());
            success(source, "Replay digest " + result.replayDigest() + ". Definition digest "
                    + result.definitionDigest() + ". Events " + result.events() + ". Totals "
                    + result.categoryTotals() + ". Matched " + result.matchedEvents()
                    + ". Mismatched " + result.mismatchedEvents() + ".");
            return result.mismatchedEvents() == 0 ? 1 : 0;
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int performanceSmoke(CommandSourceStack source) {
        try {
            PerformanceWorkload.Contract contract = PerformanceWorkload.reference();
            Path fixture = reports(source).resolve("perf_001_fixture.index");
            PerformanceWorkload.generateFixtureIndex(contract, fixture);
            var forty = PerformanceWorkload.run(contract, 40, 2_000);
            var hundred = PerformanceWorkload.run(contract, 100, 2_000);
            success(source, "Performance smoke fixture " + fixture + ".");
            success(source, "Forty players p95 nanoseconds " + forty.p95TickNanos()
                    + ". p99 " + forty.p99TickNanos() + ". Events " + forty.events() + ".");
            success(source, "One hundred players p95 nanoseconds " + hundred.p95TickNanos()
                    + ". p99 " + hundred.p99TickNanos() + ". Events " + hundred.events() + ".");
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int showCheck(CommandSourceStack source, MassCheckSavedData.Session session) {
        success(source, "Check " + (session.index() + 1) + ". "
                + MassCheckSavedData.CHECKS.get(session.index()));
        return 1;
    }

    private static int withSession(CommandSourceStack source, SessionOperation operation) {
        try {
            return operation.run(MassCheckSavedData.get(source.getServer()));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException
                 | IllegalArgumentException | IllegalStateException | IOException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static Path reports(CommandSourceStack source) throws IOException {
        Path directory = source.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("progressiveskills/reports").normalize();
        Files.createDirectories(directory);
        return directory;
    }

    private static void write(Path path, String contents) throws IOException {
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }

    private static String json(Map<String, String> values) {
        var parts = new ArrayList<String>();
        values.forEach((key, value) -> parts.add("\"" + escape(key) + "\":\"" + escape(value) + "\""));
        return "{" + String.join(",", parts) + "}";
    }

    private static String reproductionJson(ReproductionBundle bundle) {
        return "{\"format_version\":" + bundle.formatVersion()
                + ",\"bundle_id\":\"" + bundle.bundleId() + "\""
                + ",\"created_at\":\"" + bundle.createdAt() + "\""
                + ",\"definition_digest\":\"" + bundle.definitionDigest() + "\""
                + ",\"definition_generation\":" + bundle.definitionGeneration()
                + ",\"state_revision\":" + bundle.stateRevision()
                + ",\"seed\":" + bundle.deterministicSeed()
                + ",\"state\":" + longJson(bundle.redactedState())
                + ",\"events\":[" + bundle.events().stream().map(event ->
                "{\"sequence\":" + event.sequence() + ",\"type\":\"" + escape(event.type())
                        + "\",\"subject\":\"" + escape(event.subject()) + "\",\"amount\":"
                        + event.amount() + ",\"state_revision\":" + event.stateRevision()
                        + "}").collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private static String longJson(Map<String, Long> values) {
        return "{" + values.entrySet().stream().map(entry -> "\"" + escape(entry.getKey()) + "\":"
                + entry.getValue()).collect(java.util.stream.Collectors.joining(",")) + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    @FunctionalInterface
    private interface SessionOperation {
        int run(MassCheckSavedData data) throws com.mojang.brigadier.exceptions.CommandSyntaxException, IOException;
    }
}
