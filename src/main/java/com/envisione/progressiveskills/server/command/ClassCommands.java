package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.classdef.ClassAttributeGrant;
import com.envisione.progressiveskills.common.classdef.ClassGrant;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.classdef.ClassSpellGrant;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.TransactionResult;
import com.envisione.progressiveskills.server.classruntime.ClassRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class ClassCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private ClassCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("class")
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("info")
                                .then(classArgument("class").executes(context -> info(
                                        context.getSource(), classId(context, "class")
                                ))))
                        .then(Commands.literal("entitlements")
                                .executes(context -> entitlements(context.getSource())))
                        .then(Commands.literal("preview")
                                .then(Commands.literal("select")
                                        .then(classArgument("class").executes(context -> previewSelect(
                                                context.getSource(), classId(context, "class")
                                        ))))
                                .then(Commands.literal("respec")
                                        .then(classArgument("class").executes(context -> previewRespec(
                                                context.getSource(), classId(context, "class")
                                        ))))
                                .then(Commands.literal("swap")
                                        .then(classArgument("removed")
                                                .then(classArgument("replacement").executes(context -> previewSwap(
                                                        context.getSource(),
                                                        classId(context, "removed"),
                                                        classId(context, "replacement")
                                                ))))))
                        .then(Commands.literal("select")
                                .then(classArgument("class").executes(context -> select(
                                        context.getSource(), classId(context, "class")
                                ))))
                        .then(Commands.literal("respec")
                                .then(classArgument("class")
                                        .then(Commands.argument("digest", StringArgumentType.word())
                                                .executes(context -> respec(
                                                        context.getSource(), classId(context, "class"),
                                                        StringArgumentType.getString(context, "digest")
                                                )))))
                        .then(Commands.literal("swap")
                                .then(classArgument("removed")
                                        .then(classArgument("replacement")
                                                .then(Commands.argument("digest", StringArgumentType.word())
                                                        .executes(context -> swap(
                                                                context.getSource(),
                                                                classId(context, "removed"),
                                                                classId(context, "replacement"),
                                                                StringArgumentType.getString(context, "digest")
                                                        ))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>
    classArgument(String name) {
        return Commands.argument(name, ResourceLocationArgument.id())
                .suggests((context, builder) -> suggestClasses(builder));
    }

    private static int list(CommandSourceStack source) {
        try {
            var catalog = ClassRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Class catalog is unavailable")
            );
            ServerPlayer player = source.getPlayerOrException();
            var transaction = TransactionRuntime.context(source.getServer()).orElseThrow(
                    () -> new IllegalStateException("Transaction runtime is unavailable")
            );
            var snapshot = transaction.service().snapshot(player.getUUID());
            var selected = ClassProgression.selectedClasses(snapshot);
            var active = ClassProgression.activeClasses(snapshot);
            success(source, "Classes " + catalog.classes().size() + ". Selected "
                    + selected.size() + ". Active " + active.size() + ".");
            catalog.slots().values().forEach(slot -> {
                int occupancy = catalog.classesInSlot(slot.id()).stream()
                        .filter(definition -> active.contains(definition.id()))
                        .mapToInt(definition -> definition.slotCost())
                        .reduce(0, Math::addExact);
                success(source, "Slot " + slot.id() + ". Capacity " + slot.capacity()
                        + ". Occupancy " + occupancy + ". Swap policy "
                        + slot.swapPolicy().serializedName() + ".");
            });
            catalog.classes().values().forEach(definition -> success(source,
                    definition.id() + ". " + definition.presentation().display().fallback()
                            + ". Slot " + definition.slot() + ". Weight " + definition.slotCost()
                            + ". State " + (active.contains(definition.id()) ? "Active"
                            : selected.contains(definition.id()) ? "Suspended" : "Available") + "."));
            return catalog.classes().size();
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int info(CommandSourceStack source, ResourceLocation classId) {
        try {
            var catalog = ClassRuntime.catalog().orElseThrow(
                    () -> new IllegalStateException("Class catalog is unavailable")
            );
            var definition = catalog.classDefinition(classId).orElseThrow(
                    () -> new IllegalArgumentException("Unknown class " + classId)
            );
            ServerPlayer player = source.getPlayerOrException();
            var transaction = TransactionRuntime.context(source.getServer()).orElseThrow(
                    () -> new IllegalStateException("Transaction runtime is unavailable")
            );
            var snapshot = transaction.service().snapshot(player.getUUID());
            var selected = ClassProgression.selectedClasses(snapshot);
            var active = ClassProgression.activeClasses(snapshot);
            String state = active.contains(classId) ? "Active"
                    : selected.contains(classId) ? "Suspended" : "Available";
            success(source, definition.id() + ". " + definition.presentation().display().fallback()
                    + ". Slot " + definition.slot() + ". Weight " + definition.slotCost()
                    + ". Access required " + definition.accessRequired() + ". Respec allowed "
                    + definition.respecAllowed() + ". State " + state + ".");
            definition.selectionCost().ifPresent(cost -> success(source,
                    "Selection cost " + cost.amount() + " " + cost.currency() + "."));
            definition.respecCost().ifPresent(cost -> success(source,
                    "Respec cost " + cost.amount() + " " + cost.currency() + "."));
            success(source, "Exclusive tags " + values(definition.exclusiveTags()) + ".");
            success(source, "Required nodes " + values(definition.requiredNodes()) + ".");
            success(source, "Required classes " + values(definition.requiredClasses()) + ".");
            success(source, "Minimum skill levels " + levels(definition.minimumSkillLevels()) + ".");
            var starterItems = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
            definition.starterKit().ifPresent(kit -> kit.items().forEach(item ->
                    starterItems.merge(item, 1, Math::addExact)));
            if (starterItems.isEmpty()) {
                success(source, "Starter kit none.");
            } else {
                starterItems.forEach((item, count) -> success(source,
                        "Starter item " + item + ". Count " + count + "."));
            }
            for (ClassGrant grant : definition.grants()) {
                reportGrant(source, "Grant", grant);
            }
            var synergies = catalog.synergies().values().stream()
                    .filter(synergy -> synergy.requiredClasses().contains(classId))
                    .toList();
            success(source, "Synergies " + synergies.size() + ".");
            for (var synergy : synergies) {
                success(source, "Synergy " + synergy.id() + ". Enabled " + synergy.enabled()
                        + ". Required classes " + values(synergy.requiredClasses()) + ".");
                for (ClassGrant grant : synergy.grants()) {
                    reportGrant(source, "Synergy grant", grant);
                }
            }
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int entitlements(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var runtime = TransactionRuntime.context(source.getServer()).orElseThrow(
                    () -> new IllegalStateException("Transaction runtime is unavailable")
            );
            var snapshot = runtime.service().snapshot(player.getUUID());
            var selected = ClassProgression.selectedClasses(snapshot);
            var active = ClassProgression.activeClasses(snapshot);
            success(source, "Selected classes " + values(selected) + ".");
            success(source, "Active classes " + values(active) + ".");
            snapshot.ownership().forEach((key, owners) -> {
                long classOwners = owners.keySet().stream().filter(sourceId ->
                        sourceId.ownerKind().equals(com.envisione.progressiveskills.common.id.DefinitionKinds.CLASS.id())
                                || sourceId.ownerKind().equals(ClassProgression.SYNERGY_OWNER_KIND)
                ).count();
                if (classOwners > 0) {
                    success(source, "Entitlement " + key + ". Effective "
                            + snapshot.projectedValues().getOrDefault(key, 0L)
                            + ". Class sources " + classOwners + ".");
                }
            });
            return 1;
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int previewSelect(CommandSourceStack source, ResourceLocation classId) {
        try {
            return reportPreview(source, ClassRuntime.previewSelect(
                    source.getPlayerOrException(), classId
            ));
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int select(CommandSourceStack source, ResourceLocation classId) {
        try {
            return report(source, ClassRuntime.select(
                    source.getPlayerOrException(), classId, commandKey("select")
            ).transaction(), "Selected " + classId + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int previewRespec(CommandSourceStack source, ResourceLocation classId) {
        try {
            return reportPreview(source, ClassRuntime.previewRespec(
                    source.getPlayerOrException(), classId
            ));
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int respec(
            CommandSourceStack source,
            ResourceLocation classId,
            String digest
    ) {
        try {
            return report(source, ClassRuntime.respec(
                    source.getPlayerOrException(), classId, digest, commandKey("respec")
            ).transaction(), "Respecced " + classId + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int previewSwap(
            CommandSourceStack source,
            ResourceLocation removed,
            ResourceLocation replacement
    ) {
        try {
            return reportPreview(source, ClassRuntime.previewSwap(
                    source.getPlayerOrException(), removed, replacement
            ));
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int swap(
            CommandSourceStack source,
            ResourceLocation removed,
            ResourceLocation replacement,
            String digest
    ) {
        try {
            return report(source, ClassRuntime.swap(
                    source.getPlayerOrException(), removed, replacement, digest, commandKey("swap")
            ).transaction(), "Swapped " + removed + " for " + replacement + ".");
        } catch (Exception exception) {
            return fail(source, exception);
        }
    }

    private static int reportPreview(
            CommandSourceStack source,
            ClassProgression.ChangePreview preview
    ) throws CommandSyntaxException {
        if (preview.allowed()) {
            success(source, "Class change Available.");
        } else {
            failure(source, "Class change Blocked.");
        }
        success(source, "Affected classes " + values(preview.affectedClasses()) + ".");
        reportAffectedDefinitions(source, preview);
        preview.costBalances().forEach((currency, amount) ->
                success(source, "Cost " + amount + " " + currency + "."));
        if (preview.costBalances().isEmpty()) {
            success(source, "Cost none.");
        }
        preview.blockers().forEach(blocker -> failure(source, "Blocker " + blocker + "."));
        if (preview.allowed() && preview.kind() != ClassProgression.ChangeKind.SELECT) {
            success(source, "Confirmation digest " + preview.digest() + ".");
        }
        return preview.allowed() ? 1 : 0;
    }

    private static void reportAffectedDefinitions(
            CommandSourceStack source,
            ClassProgression.ChangePreview preview
    ) throws CommandSyntaxException {
        var catalog = ClassRuntime.catalog().orElseThrow(
                () -> new IllegalStateException("Class catalog is unavailable")
        );
        var transaction = TransactionRuntime.context(source.getServer()).orElseThrow(
                () -> new IllegalStateException("Transaction runtime is unavailable")
        );
        var snapshot = transaction.service().snapshot(source.getPlayerOrException().getUUID());
        var selected = ClassProgression.selectedClasses(snapshot);
        var active = ClassProgression.activeClasses(snapshot);
        preview.affectedClasses().forEach(classId -> catalog.classDefinition(classId).ifPresent(definition -> {
            String state = active.contains(classId) ? "Active"
                    : selected.contains(classId) ? "Suspended" : "Available";
            success(source, "Affected class " + classId + ". Current state " + state
                    + ". Grants " + grantIds(definition.grants()) + ".");
        }));
        catalog.synergies().values().stream()
                .filter(synergy -> synergy.requiredClasses().stream()
                        .anyMatch(preview.affectedClasses()::contains))
                .forEach(synergy -> success(source, "Related synergy " + synergy.id()
                        + ". Required classes " + values(synergy.requiredClasses())
                        + ". Grants " + grantIds(synergy.grants()) + "."));
    }

    private static int report(CommandSourceStack source, TransactionResult result, String message) {
        if (!result.status().committed()) {
            failure(source, result.diagnosticCode() + ". " + result.message());
            return 0;
        }
        success(source, message + " Transaction " + result.transactionId() + ".");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestClasses(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                ClassRuntime.catalog().stream().flatMap(catalog -> catalog.classes().keySet().stream())
                        .map(ResourceLocation::toString),
                builder
        );
    }

    private static ResourceLocation classId(
            CommandContext<CommandSourceStack> context,
            String argument
    ) {
        return ResourceLocationArgument.getId(context, argument);
    }

    private static IdempotencyKey commandKey(String action) {
        return new IdempotencyKey("phase11/command/" + action + "/" + UUID.randomUUID());
    }

    private static String values(java.util.Collection<ResourceLocation> values) {
        return values.isEmpty() ? "none" : String.join(", ", values.stream()
                .map(ResourceLocation::toString).toList());
    }

    private static String levels(java.util.Map<ResourceLocation, Integer> values) {
        return values.isEmpty() ? "none" : String.join(", ", values.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue()).toList());
    }

    private static String grantIds(java.util.Collection<ClassGrant> grants) {
        return grants.isEmpty() ? "none" : String.join(", ", grants.stream()
                .map(grant -> grant.id().toString()).toList());
    }

    private static void reportGrant(CommandSourceStack source, String label, ClassGrant grant) {
        String operation;
        if (grant instanceof ClassAttributeGrant attribute) {
            operation = attribute.operation().serializedName();
        } else if (grant instanceof ClassSpellGrant spell) {
            operation = spell.learningPolicy().serializedName();
        } else {
            operation = "owned";
        }
        success(source, label + " " + grant.id() + ". Type " + grant.type().serializedName()
                + ". Target " + grant.targetId() + ". Operation " + operation
                + ". Resolver " + grant.resolver().name().toLowerCase()
                + ". Value " + grant.value() + ".");
    }

    private static int fail(CommandSourceStack source, Throwable throwable) {
        failure(source, safeMessage(throwable));
        return 0;
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
