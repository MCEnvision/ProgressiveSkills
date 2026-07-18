package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.server.studio.StudioRuntime;
import com.envisione.progressiveskills.server.studio.StudioService;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class StudioCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private StudioCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pskills")
                .then(Commands.literal("studio").requires(source -> source.hasPermission(4))
                        .then(Commands.literal("draft")
                                .then(Commands.literal("create")
                                        .then(Commands.argument("namespace", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> create(context.getSource(),
                                                                StringArgumentType.getString(context, "namespace"),
                                                                StringArgumentType.getString(context, "name"))))))
                                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                                .then(Commands.literal("status")
                                        .then(Commands.argument("draft", ResourceLocationArgument.id())
                                                .executes(context -> status(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "draft"))))))
                        .then(Commands.literal("file")
                                .then(Commands.literal("put")
                                        .then(Commands.argument("draft", ResourceLocationArgument.id())
                                                .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                        .then(Commands.argument("path", StringArgumentType.word())
                                                                .then(Commands.argument("contents", StringArgumentType.greedyString())
                                                                        .executes(context -> put(context.getSource(),
                                                                                ResourceLocationArgument.getId(context, "draft"),
                                                                                LongArgumentType.getLong(context, "revision"),
                                                                                StringArgumentType.getString(context, "path"),
                                                                                StringArgumentType.getString(context, "contents"))))))))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("draft", ResourceLocationArgument.id())
                                                .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                                                .executes(context -> delete(context.getSource(),
                                                                        ResourceLocationArgument.getId(context, "draft"),
                                                                        LongArgumentType.getLong(context, "revision"),
                                                                        StringArgumentType.getString(context, "path"))))))))
                        .then(Commands.literal("lint")
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .executes(context -> lint(context.getSource(),
                                                ResourceLocationArgument.getId(context, "draft")))))
                        .then(Commands.literal("diff")
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .executes(context -> diff(context.getSource(),
                                                ResourceLocationArgument.getId(context, "draft")))))
                        .then(Commands.literal("history")
                                .then(Commands.literal("restore")
                                        .then(Commands.argument("draft", ResourceLocationArgument.id())
                                                .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                        .then(Commands.argument("history_revision",
                                                                        LongArgumentType.longArg(0))
                                                                .executes(context -> restoreHistory(
                                                                        context.getSource(),
                                                                        ResourceLocationArgument.getId(
                                                                                context, "draft"),
                                                                        LongArgumentType.getLong(
                                                                                context, "revision"),
                                                                        LongArgumentType.getLong(
                                                                                context, "history_revision")))))))
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .executes(context -> history(context.getSource(),
                                                ResourceLocationArgument.getId(context, "draft")))))
                        .then(Commands.literal("rebase")
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                .executes(context -> rebase(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "draft"),
                                                        LongArgumentType.getLong(context, "revision"))))))
                        .then(Commands.literal("publish")
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                .then(Commands.argument("digest", StringArgumentType.word())
                                                        .executes(context -> publish(context.getSource(),
                                                                ResourceLocationArgument.getId(context, "draft"),
                                                                LongArgumentType.getLong(context, "revision"),
                                                                StringArgumentType.getString(context, "digest")))))))
                        .then(Commands.literal("rollback")
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                .executes(context -> rollback(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "draft"),
                                                        LongArgumentType.getLong(context, "revision"))))))
                        .then(Commands.literal("export")
                                .then(Commands.argument("draft", ResourceLocationArgument.id())
                                        .executes(context -> exportPack(context.getSource(),
                                                ResourceLocationArgument.getId(context, "draft")))))
                        .then(Commands.literal("import")
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .then(Commands.argument("file", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> importPack(context.getSource(),
                                                                StringArgumentType.getString(context, "namespace"),
                                                                StringArgumentType.getString(context, "file"),
                                                                StringArgumentType.getString(context, "name"))))))
                        .then(Commands.literal("record")
                                .then(Commands.literal("start").executes(context -> recordStart(context.getSource())))
                                .then(Commands.literal("mark")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("subject", ResourceLocationArgument.id())
                                                        .then(Commands.argument("value", LongArgumentType.longArg())
                                                                .executes(context -> recordMark(context.getSource(),
                                                                        StringArgumentType.getString(context, "type"),
                                                                        ResourceLocationArgument.getId(context, "subject"),
                                                                        LongArgumentType.getLong(context, "value")))))))
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("draft", ResourceLocationArgument.id())
                                                .then(Commands.argument("revision", LongArgumentType.longArg(0))
                                                        .then(Commands.argument("fixture", ResourceLocationArgument.id())
                                                                .executes(context -> recordStop(context.getSource(),
                                                                        ResourceLocationArgument.getId(context, "draft"),
                                                                        LongArgumentType.getLong(context, "revision"),
                                                                        ResourceLocationArgument.getId(context, "fixture"))))))))
                        .then(Commands.literal("palette").executes(context -> palette(context.getSource())))
                        .then(Commands.literal("graph")
                                .then(Commands.literal("export")
                                        .then(Commands.argument("draft", ResourceLocationArgument.id())
                                                .executes(context -> graph(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "draft")))))))));
    }

    private static int create(CommandSourceStack source, String namespace, String name) {
        return run(source, player -> {
            var draft = StudioService.create(source.getServer(), player.getUUID(), namespace, name);
            success(source, "Draft " + draft.id() + ". Revision " + draft.revision() + ".");
        });
    }

    private static int list(CommandSourceStack source) {
        return run(source, player -> StudioService.list(source.getServer()).forEach(draft ->
                success(source, draft.id() + ". Revision " + draft.revision() + ". Status "
                        + draft.status() + ". Owner " + draft.owner() + ".")));
    }

    private static int status(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> success(source, StudioService.find(source.getServer(), id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Studio draft " + id)).toString() + "."));
    }

    private static int put(
            CommandSourceStack source,
            ResourceLocation id,
            long revision,
            String path,
            String contents
    ) {
        return run(source, player -> {
            var draft = StudioService.write(source.getServer(), id, player.getUUID(), revision, path,
                    unescape(contents));
            success(source, "Draft revision " + draft.revision() + ".");
        });
    }

    private static int delete(CommandSourceStack source, ResourceLocation id, long revision, String path) {
        return run(source, player -> success(source, "Draft revision "
                + StudioService.delete(source.getServer(), id, player.getUUID(), revision, path).revision() + "."));
    }

    private static int lint(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> {
            var result = StudioService.lint(source.getServer(), id);
            success(source, "Valid " + result.valid() + ". Digest " + result.digest() + ". Files "
                    + result.files() + ". Bytes " + result.bytes() + ". Issues " + result.issues() + ".");
        });
    }

    private static int diff(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> {
            var diff = StudioService.diff(source.getServer(), id);
            success(source, "Digest " + diff.digest() + ". Files " + diff.entries().size()
                    + ". Bytes " + diff.bytes() + ".");
            diff.entries().forEach(entry -> success(source,
                    entry.path() + ". " + entry.digest() + ". " + entry.bytes() + " bytes."));
        });
    }

    private static int history(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> success(source, "History " + StudioService.history(source.getServer(), id) + "."));
    }

    private static int restoreHistory(
            CommandSourceStack source,
            ResourceLocation id,
            long revision,
            long historyRevision
    ) {
        return run(source, player -> success(source, "Draft revision "
                + StudioService.restoreHistory(
                        source.getServer(), id, player.getUUID(), revision, historyRevision).revision() + "."));
    }

    private static int rebase(CommandSourceStack source, ResourceLocation id, long revision) {
        return run(source, player -> success(source, "Draft revision "
                + StudioService.rebase(source.getServer(), id, player.getUUID(), revision).revision() + "."));
    }

    private static int publish(CommandSourceStack source, ResourceLocation id, long revision, String digest) {
        return run(source, player -> success(source,
                StudioService.publish(source.getServer(), id, player.getUUID(), revision, digest).message() + "."));
    }

    private static int rollback(CommandSourceStack source, ResourceLocation id, long revision) {
        return run(source, player -> success(source,
                StudioService.rollback(source.getServer(), id, player.getUUID(), revision).message() + "."));
    }

    private static int exportPack(CommandSourceStack source, ResourceLocation id) {
        return run(source, player -> success(source, "Exported to "
                + StudioService.exportPack(source.getServer(), id, player.getUUID()) + "."));
    }

    private static int importPack(CommandSourceStack source, String namespace, String file, String name) {
        return run(source, player -> {
            if (!file.matches("[a-zA-Z0-9_.-]+\\.pspack")) {
                throw new IllegalArgumentException("Studio import filename is invalid");
            }
            Path imports = StudioService.importDirectory(source.getServer());
            Path archive = imports.resolve(file).normalize();
            if (!archive.startsWith(imports)) {
                throw new IllegalArgumentException("Studio import path escapes its root");
            }
            var draft = StudioService.importPack(source.getServer(), player.getUUID(), namespace, name, archive);
            success(source, "Imported draft " + draft.id() + ". Revision " + draft.revision() + ".");
        });
    }

    private static int recordStart(CommandSourceStack source) {
        return run(source, player -> {
            StudioRuntime.startRecording(player.getUUID());
            success(source, "Studio recording started.");
        });
    }

    private static int recordMark(
            CommandSourceStack source,
            String type,
            ResourceLocation subject,
            long value
    ) {
        return run(source, player -> {
            StudioRuntime.record(player.getUUID(), type, subject.toString(), value);
            success(source, "Studio event recorded.");
        });
    }

    private static int recordStop(
            CommandSourceStack source,
            ResourceLocation draft,
            long revision,
            ResourceLocation fixture
    ) {
        return run(source, player -> {
            var recording = StudioRuntime.finishRecording(player.getUUID());
            String path = "simulations/" + fixture.getPath() + ".toml";
            var updated = StudioService.write(source.getServer(), draft, player.getUUID(), revision, path,
                    StudioRuntime.fixture(recording, fixture));
            success(source, "Recorded fixture saved. Revision " + updated.revision() + ".");
        });
    }

    private static int palette(CommandSourceStack source) {
        success(source, "Open the command palette key and choose Studio actions or use pskills studio commands.");
        return 1;
    }

    private static int graph(CommandSourceStack source, ResourceLocation draft) {
        return run(source, player -> {
            var diff = StudioService.diff(source.getServer(), draft);
            success(source, "graph studio " + draft + ". digest " + diff.digest() + ".");
            diff.entries().forEach(entry -> success(source, "node " + entry.path() + ". " + entry.digest() + "."));
        });
    }

    private static int run(CommandSourceStack source, StudioOperation operation) {
        try {
            operation.run(source.getPlayerOrException());
            return 1;
        } catch (Exception exception) {
            failure(source, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return 0;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t");
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }

    @FunctionalInterface
    private interface StudioOperation {
        void run(ServerPlayer player) throws Exception;
    }
}
