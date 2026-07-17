package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.diagnostic.Diagnostic;
import com.envisione.progressiveskills.common.diagnostic.DiagnosticSeverity;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.id.StableId;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.pack.CanonicalSemanticDigest;
import com.envisione.progressiveskills.common.pack.DefinitionRegistryService;
import com.envisione.progressiveskills.common.pack.LivePackState;
import com.envisione.progressiveskills.common.pack.PublishResult;
import com.envisione.progressiveskills.common.pack.SemanticDiff;
import com.envisione.progressiveskills.common.pack.StageAttempt;
import com.envisione.progressiveskills.common.pack.StagingResult;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Locale;

/** Phase 3 operator commands for validation, staged reload, diff, publish, and inspection. */
@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class PackCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[ProgressiveSkills] ";
    private static final int MAX_DIAGNOSTICS_IN_CHAT = 8;
    private static final int MAX_PROVENANCE_FIELDS_IN_CHAT = 16;

    private PackCommands() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        var infoDefinition = Commands.argument("kind", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        DefinitionKinds.all().stream().map(kind -> kind.id().toString()), builder
                ))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((context, builder) -> suggestDefinitionIds(
                                context.getSource(),
                                ResourceLocationArgument.getId(context, "kind"),
                                builder
                        ))
                        .executes(context -> infoDefinition(context.getSource(),
                                ResourceLocationArgument.getId(context, "kind"),
                                ResourceLocationArgument.getId(context, "id"), false))
                        .then(Commands.literal("--provenance")
                                .executes(context -> infoDefinition(context.getSource(),
                                        ResourceLocationArgument.getId(context, "kind"),
                                        ResourceLocationArgument.getId(context, "id"), true))));

        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("status").requires(source -> source.hasPermission(2))
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("validate").requires(source -> source.hasPermission(2))
                        .executes(context -> validate(context.getSource())))
                .then(Commands.literal("reload").requires(source -> source.hasPermission(2))
                        .executes(context -> dryRun(context.getSource(), true))
                        .then(Commands.literal("--dry-run")
                                .executes(context -> dryRun(context.getSource(), true)))
                        .then(Commands.literal("--publish").requires(source -> source.hasPermission(4))
                                .executes(context -> publish(context.getSource()))))
                .then(Commands.literal("diff").requires(source -> source.hasPermission(2))
                        .executes(context -> diff(context.getSource())))
                .then(Commands.literal("info").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("pack")
                                .then(Commands.argument("pack_id", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> {
                                            var service = PackRuntime.service();
                                            return SharedSuggestionProvider.suggest(
                                                    service.stream().flatMap(value -> value.live().snapshot()
                                                            .manifests().keySet().stream().map(Object::toString)),
                                                    builder
                                            );
                                        })
                                        .executes(context -> infoPack(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(context, "pack_id")
                                        ))))
                        .then(infoDefinition)));
    }

    private static int help(CommandSourceStack source) {
        success(source, "Available: /ps status, validate, reload --dry-run|--publish, diff, info pack, "
                + "info <kind> <id>, lifecycle status|demo|coowner|recompute|revoke|audit|selftest, "
                + "persistence status|snapshot|export, network status|resync, xp, xp source, skill get, "
                + "rule status, explain xp last, tree list|info|preview|buy|refund|respec");
        return 1;
    }

    private static int status(CommandSourceStack source) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        LivePackState live = service.live();
        success(source, "Live generation " + live.generation() + ": "
                + live.snapshot().manifests().size() + " packs, "
                + live.snapshot().canonicalIr().definitions().size() + " definitions, digest "
                + live.snapshot().contentDigest() + (live.recovered() ? " (recovered)" : ""));
        if (service.staged().isPresent()) {
            success(source, "A dry-run candidate is staged from " + service.staged().orElseThrow().stagedAt());
        }
        return live.generation() > 0 ? 1 : 0;
    }

    private static int dryRun(CommandSourceStack source, boolean showDiff) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        StageAttempt attempt = service.stageDryRun();
        long errors = attempt.result().diagnostics().diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR).count();
        long warnings = attempt.result().diagnostics().diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING).count();
        if (!attempt.result().valid()) {
            failure(source, "Validation failed: " + errors + " errors, " + warnings + " warnings; live generation unchanged");
            sendDiagnostics(source, attempt.result());
            return 0;
        }
        var snapshot = attempt.result().snapshot().orElseThrow();
        success(source, (showDiff ? "Dry-run staged" : "Validation passed") + ": "
                + snapshot.manifests().size() + " packs, "
                + snapshot.canonicalIr().definitions().size() + " definitions, "
                + warnings + " warnings, digest " + snapshot.contentDigest());
        if (warnings > 0) {
            sendDiagnostics(source, attempt.result());
        }
        if (showDiff) {
            sendDiff(source, attempt.diff().orElseThrow());
            success(source, "Review complete? Run /ps reload --publish before changing pack files.");
        }
        return 1;
    }

    private static int validate(CommandSourceStack source) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        StagingResult result = service.validate();
        long errors = result.diagnostics().diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR).count();
        long warnings = result.diagnostics().diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING).count();
        if (!result.valid()) {
            failure(source, "Validation failed: " + errors + " errors, " + warnings
                    + " warnings; live generation unchanged");
            sendDiagnostics(source, result);
            return 0;
        }
        var snapshot = result.snapshot().orElseThrow();
        success(source, "Validation passed: " + snapshot.manifests().size() + " packs, "
                + snapshot.canonicalIr().definitions().size() + " definitions, "
                + warnings + " warnings, digest " + snapshot.contentDigest());
        if (warnings > 0) {
            sendDiagnostics(source, result);
        }
        return 1;
    }

    private static int publish(CommandSourceStack source) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        PublishResult result;
        try {
            result = service.publishStaged();
        } catch (IOException | ArithmeticException | IllegalArgumentException exception) {
            failure(source, "Publication failed before the live registry changed: " + safeMessage(exception));
            return 0;
        }
        if (!result.published()) {
            failure(source, result.message());
            return 0;
        }
        success(source, result.message() + "; digest "
                + result.liveState().orElseThrow().snapshot().contentDigest());
        TransactionRuntime.onDefinitionsPublished(source.getServer());
        return 1;
    }

    private static int diff(CommandSourceStack source) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        if (service.staged().isEmpty() || service.staged().orElseThrow().diff().isEmpty()) {
            failure(source, "No valid dry-run diff is staged; run /ps reload --dry-run");
            return 0;
        }
        sendDiff(source, service.staged().orElseThrow().diff().orElseThrow());
        return 1;
    }

    private static int infoPack(CommandSourceStack source, ResourceLocation id) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        try {
            StableId.requireValid(id);
            var manifest = service.live().snapshot().manifests().get(id);
            if (manifest == null) {
                failure(source, "Unknown live pack: " + id);
                return 0;
            }
            success(source, "Pack " + id + " v" + manifest.contentVersion() + ", namespace "
                    + manifest.namespace() + ", priority " + manifest.priority() + ", engine "
                    + manifest.engine().expression());
            return 1;
        } catch (IllegalArgumentException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static int infoDefinition(
            CommandSourceStack source,
            ResourceLocation authoredKind,
            ResourceLocation authoredId,
            boolean provenance
    ) {
        DefinitionRegistryService service = service(source);
        if (service == null) {
            return 0;
        }
        try {
            var kind = DefinitionKinds.require(StableId.requireValid(authoredKind));
            var key = new DefinitionKey(kind, StableId.requireValid(authoredId));
            var definition = service.live().snapshot().canonicalIr().definitions().get(key);
            if (definition == null) {
                failure(source, "Unknown live definition: " + key);
                return 0;
            }
            boolean disabled = service.live().snapshot().disabledDefinitions().contains(key);
            success(source, key + " schema v" + definition.header().schemaVersion()
                    + (disabled ? " [disabled]" : " [enabled]")
                    + ", semantic digest " + CanonicalSemanticDigest.definition(definition));
            CanonicalValue value = definition.fields().fields().get("value");
            if (value instanceof CanonicalValue.ComponentValue component) {
                success(source, "Component fallback: \"" + preview(component.value().fallback()) + "\""
                        + component.value().localizationKey().map(localizationKey ->
                        "; locale key " + localizationKey).orElse(""));
            } else if (value instanceof CanonicalValue.IconValue icon) {
                success(source, "Icon " + icon.value().kind().serializedName() + " "
                        + icon.value().references() + "; fallback " + icon.value().fallback());
            }
            if (provenance) {
                success(source, "Source " + definition.provenance().packId() + "/"
                        + definition.provenance().sourcePath() + " via " + definition.provenance().adapter()
                        + "; " + definition.sourceMap().fields().size() + " field spans");
                int shown = 0;
                for (var entry : definition.sourceMap().fields().entrySet()) {
                    if (shown >= MAX_PROVENANCE_FIELDS_IN_CHAT) {
                        break;
                    }
                    var reference = entry.getValue();
                    success(source, entry.getKey() + " <- " + reference.provenance().packId() + "/"
                            + reference.provenance().sourcePath() + ":"
                            + reference.span().start().line());
                    shown++;
                }
                int remaining = definition.sourceMap().fields().size()
                        - Math.min(shown, definition.sourceMap().fields().size());
                if (remaining > 0) {
                    success(source, remaining + " additional field provenance entries omitted from chat");
                }
            }
            return 1;
        } catch (IllegalArgumentException exception) {
            failure(source, safeMessage(exception));
            return 0;
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDefinitionIds(
            CommandSourceStack source,
            ResourceLocation authoredKind,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        try {
            var kind = DefinitionKinds.require(StableId.requireValid(authoredKind));
            return SharedSuggestionProvider.suggest(
                    PackRuntime.service().stream().flatMap(service -> service.live().snapshot().canonicalIr()
                            .definitions().keySet().stream()
                            .filter(key -> key.kind().equals(kind))
                            .map(key -> key.id().toString())),
                    builder
            );
        } catch (IllegalArgumentException exception) {
            return builder.buildFuture();
        }
    }

    private static void sendDiagnostics(CommandSourceStack source, StagingResult result) {
        for (Diagnostic diagnostic : result.diagnostics().diagnostics()) {
            String location = diagnostic.source().map(reference ->
                    reference.provenance().packId() + "/" + reference.provenance().sourcePath()
            ).orElse("no source");
            if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
                LOGGER.error("[ProgressiveSkills] {} {}: {}", diagnostic.descriptor().code(), location,
                        diagnostic.message());
            } else {
                LOGGER.warn("[ProgressiveSkills] {} {}: {}", diagnostic.descriptor().code(), location,
                        diagnostic.message());
            }
        }
        int shown = 0;
        for (Diagnostic diagnostic : result.diagnostics().diagnostics()) {
            if (shown >= MAX_DIAGNOSTICS_IN_CHAT) {
                break;
            }
            String location = diagnostic.source().map(reference -> reference.provenance().sourcePath() + ": ").orElse("");
            String message = diagnostic.descriptor().code() + " " + location + diagnostic.message()
                    + " Fix: " + diagnostic.descriptor().suggestedFix();
            if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
                failure(source, message);
            } else {
                success(source, "Warning: " + message);
            }
            shown++;
        }
        int remaining = result.diagnostics().diagnostics().size() - Math.min(
                shown, result.diagnostics().diagnostics().size()
        );
        if (remaining > 0) {
            String message = remaining + " additional diagnostics were written to the server log";
            if (result.diagnostics().hasErrors()) {
                failure(source, message);
            } else {
                success(source, "Warning: " + message);
            }
        }
    }

    private static void sendDiff(CommandSourceStack source, SemanticDiff diff) {
        success(source, "Diff " + shortDigest(diff.fromDigest()) + " -> " + shortDigest(diff.toDigest())
                + ": definitions +" + diff.definitions(SemanticDiff.ChangeKind.ADDED)
                + " -" + diff.definitions(SemanticDiff.ChangeKind.REMOVED)
                + " ~" + diff.definitions(SemanticDiff.ChangeKind.MODIFIED)
                + "; packs +" + diff.packs(SemanticDiff.ChangeKind.ADDED)
                + " -" + diff.packs(SemanticDiff.ChangeKind.REMOVED)
                + " ~" + diff.packs(SemanticDiff.ChangeKind.MODIFIED)
                + (diff.aliasesChanged() ? "; aliases changed" : "")
                + (diff.disabledSetChanged() ? "; availability changed" : ""));
    }

    private static DefinitionRegistryService service(CommandSourceStack source) {
        var service = PackRuntime.service();
        if (service.isEmpty()) {
            failure(source, "The content-pack registry is unavailable; inspect the server startup log");
            return null;
        }
        return service.orElseThrow();
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    private static String shortDigest(String digest) {
        return digest.substring(0, 12);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName().toLowerCase(Locale.ROOT)
                : message;
    }

    private static String preview(String value) {
        String normalized = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 253) + "...";
    }
}
