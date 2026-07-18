package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.creator.BuildShareCode;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.server.creator.CreatorProgressSavedData;
import com.envisione.progressiveskills.server.creator.CreatorRuntime;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@EventBusSubscriber(modid = ProjectIdentity.MOD_ID)
public final class CreatorCommands {
    private static final String PREFIX = "[ProgressiveSkills] ";

    private CreatorCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ps")
                .then(Commands.literal("creator")
                        .then(Commands.literal("catalog").executes(context -> catalog(context.getSource())))
                        .then(Commands.literal("simulate")
                                .then(Commands.argument("formula", StringArgumentType.greedyString())
                                        .executes(context -> simulate(context.getSource(),
                                                StringArgumentType.getString(context, "formula"))))))
                .then(Commands.literal("resource")
                        .then(Commands.literal("get")
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .executes(context -> resource(context.getSource(),
                                                ResourceLocationArgument.getId(context, "resource"), 0L))))
                        .then(Commands.literal("add").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .then(Commands.argument("amount", LongArgumentType.longArg())
                                                .executes(context -> resource(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "resource"),
                                                        LongArgumentType.getLong(context, "amount")))))))
                .then(Commands.literal("convert")
                        .then(Commands.argument("conversion", ResourceLocationArgument.id())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(context -> convert(context.getSource(),
                                                ResourceLocationArgument.getId(context, "conversion"),
                                                LongArgumentType.getLong(context, "amount"))))))
                .then(Commands.literal("prestige")
                        .then(Commands.argument("track", ResourceLocationArgument.id())
                                .executes(context -> prestige(context.getSource(),
                                        ResourceLocationArgument.getId(context, "track")))))
                .then(Commands.literal("milestone")
                        .then(Commands.literal("choose")
                                .then(Commands.argument("milestone", ResourceLocationArgument.id())
                                        .then(Commands.argument("choice", ResourceLocationArgument.id())
                                                .executes(context -> milestone(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "milestone"),
                                                        ResourceLocationArgument.getId(context, "choice")))))))
                .then(Commands.literal("contract")
                        .then(Commands.literal("assign")
                                .then(Commands.argument("contract", ResourceLocationArgument.id())
                                        .executes(context -> contractAssign(context.getSource(),
                                                ResourceLocationArgument.getId(context, "contract")))))
                        .then(Commands.literal("status").executes(context -> contractStatus(context.getSource())))
                        .then(Commands.literal("progress").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("contract", ResourceLocationArgument.id())
                                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                        .executes(context -> contractProgress(context.getSource(),
                                                                ResourceLocationArgument.getId(context, "contract"),
                                                                ResourceLocationArgument.getId(context, "skill"),
                                                                LongArgumentType.getLong(context, "amount"))))))))
                .then(Commands.literal("combo")
                        .then(Commands.argument("combo", ResourceLocationArgument.id())
                                .then(Commands.argument("award", ResourceLocationArgument.id())
                                        .executes(context -> combo(context.getSource(),
                                                ResourceLocationArgument.getId(context, "combo"),
                                                ResourceLocationArgument.getId(context, "award"))))))
                .then(Commands.literal("loadout")
                        .then(Commands.literal("save")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> loadoutSave(context.getSource(),
                                                ResourceLocationArgument.getId(context, "id")))))
                        .then(Commands.literal("code")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> loadoutCode(context.getSource(),
                                                ResourceLocationArgument.getId(context, "id")))))
                        .then(Commands.literal("apply")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> loadoutApply(context.getSource(),
                                                ResourceLocationArgument.getId(context, "id"))))))
                .then(Commands.literal("build")
                        .then(Commands.literal("code").executes(context -> buildCode(context.getSource())))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> buildInspect(context.getSource(),
                                                StringArgumentType.getString(context, "code")))))
                        .then(Commands.literal("apply")
                                .then(Commands.argument("code", StringArgumentType.greedyString())
                                        .executes(context -> buildApply(context.getSource(),
                                                StringArgumentType.getString(context, "code")))))));
    }

    private static int catalog(CommandSourceStack source) {
        var catalog = CreatorRuntime.catalog();
        if (catalog.isEmpty()) {
            failure(source, "Creator catalog is unavailable.");
            return 0;
        }
        success(source, "Creator definitions " + catalog.orElseThrow().definitions().size() + ".");
        catalog.orElseThrow().definitions().keySet().forEach(key -> success(source, key.toString() + "."));
        return 1;
    }

    private static int simulate(CommandSourceStack source, String formula) {
        try {
            long result = CreatorRuntime.simulate(player(source), formula);
            success(source, "Simulation result " + FixedPoint.format(result) + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int resource(CommandSourceStack source, ResourceLocation id, long delta) {
        try {
            ServerPlayer player = player(source);
            long value = CreatorRuntime.adjustResource(player, id, delta);
            success(source, id + " resource " + value + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int convert(CommandSourceStack source, ResourceLocation id, long amount) {
        try {
            var result = CreatorRuntime.convert(player(source), id, amount, UUID.randomUUID());
            if (!result.status().committed()) {
                failure(source, result.message());
                return 0;
            }
            success(source, "Conversion committed.");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int prestige(CommandSourceStack source, ResourceLocation id) {
        try {
            int value = CreatorRuntime.prestige(player(source), id);
            success(source, id + " prestige rank " + value + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int milestone(CommandSourceStack source, ResourceLocation milestone, ResourceLocation choice) {
        try {
            CreatorRuntime.chooseMilestone(player(source), milestone, choice);
            success(source, "Milestone choice saved.");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int contractAssign(CommandSourceStack source, ResourceLocation id) {
        try {
            long epoch = LocalDate.now(ZoneOffset.UTC).toEpochDay();
            var contract = CreatorRuntime.assignContract(player(source), id, epoch);
            success(source, "Training contract " + contract.skill() + ". Goal " + contract.goal() + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int contractStatus(CommandSourceStack source) {
        try {
            ServerPlayer player = player(source);
            var contracts = CreatorProgressSavedData.get(source.getServer()).state(player.getUUID()).contracts();
            if (contracts.isEmpty()) {
                success(source, "No training contract is assigned.");
            } else {
                contracts.forEach((id, value) -> success(source, id + ". " + value.skill() + ". "
                        + value.progress() + " of " + value.goal() + ". Complete " + value.complete() + "."));
            }
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int contractProgress(
            CommandSourceStack source,
            ResourceLocation contract,
            ResourceLocation skill,
            long amount
    ) {
        try {
            ServerPlayer player = player(source);
            long epoch = LocalDate.now(ZoneOffset.UTC).toEpochDay();
            var value = CreatorProgressSavedData.get(source.getServer()).progressContract(
                    player.getUUID(), contract, skill, amount, epoch);
            success(source, "Training progress " + value.progress() + " of " + value.goal() + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int combo(CommandSourceStack source, ResourceLocation combo, ResourceLocation award) {
        try {
            var value = CreatorRuntime.awardCombo(player(source), combo, award);
            success(source, "Combo step " + value.index() + ". Mastery " + value.mastery() + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int loadoutSave(CommandSourceStack source, ResourceLocation id) {
        try {
            String code = CreatorRuntime.saveLoadout(player(source), id);
            success(source, "Loadout saved. " + code + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int loadoutCode(CommandSourceStack source, ResourceLocation id) {
        try {
            ServerPlayer player = player(source);
            var loadout = CreatorProgressSavedData.get(source.getServer()).loadout(player.getUUID(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown loadout " + id));
            success(source, loadout.buildCode());
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int loadoutApply(CommandSourceStack source, ResourceLocation id) {
        try {
            var result = CreatorRuntime.applyLoadout(player(source), id, UUID.randomUUID());
            if (!result.status().committed()) {
                throw new IllegalStateException(result.message());
            }
            success(source, "Loadout applied.");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int buildCode(CommandSourceStack source) {
        try {
            success(source, BuildShareCode.encode(CreatorRuntime.currentBuild(player(source))));
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int buildInspect(CommandSourceStack source, String code) {
        try {
            var build = BuildShareCode.decode(code);
            success(source, "Digest " + build.definitionDigest() + ". Classes " + build.classes()
                    + ". Nodes " + build.nodes() + ". Abilities " + build.abilities() + ".");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static int buildApply(CommandSourceStack source, String code) {
        try {
            var result = CreatorRuntime.applyBuild(
                    player(source), BuildShareCode.decode(code), UUID.randomUUID());
            if (!result.status().committed()) {
                throw new IllegalStateException(result.message());
            }
            success(source, "Build applied.");
            return 1;
        } catch (Exception exception) {
            return failed(source, exception);
        }
    }

    private static ServerPlayer player(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int failed(CommandSourceStack source, Exception exception) {
        failure(source, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        return 0;
    }

    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(PREFIX + message), false);
    }

    private static void failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(PREFIX + message));
    }
}
