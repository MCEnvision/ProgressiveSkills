package com.envisione.progressiveskills.gametest;

import com.envisione.progressiveskills.ProjectIdentity;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Real-server proof for the staged pack workflow and Phase 4 lifecycle invariants. */
@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressiveSkillsGameTests {
    private ProgressiveSkillsGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void harnessLoads(GameTestHelper helper) {
        helper.assertTrue(
                ModList.get().isLoaded(ProjectIdentity.MOD_ID),
                "ProgressiveSkills must be loaded"
        );
        try {
            var server = helper.getLevel().getServer();
            var source = server.createCommandSourceStack().withPermission(4);
            int status = server.getCommands().getDispatcher().execute("ps status", source);
            int validate = server.getCommands().getDispatcher().execute("ps validate", source);
            int dryRun = server.getCommands().getDispatcher().execute("ps reload --dry-run", source);
            int diff = server.getCommands().getDispatcher().execute("ps diff", source);
            int info = server.getCommands().getDispatcher().execute(
                    "ps info progressiveskills:component_spec progressiveskills:engine_name --provenance",
                    source
            );
            int lifecycle = server.getCommands().getDispatcher().execute("ps lifecycle selftest", source);
            helper.assertTrue(status == 1, "/ps status must report a live Phase 3 generation");
            helper.assertTrue(validate == 1, "/ps validate must accept the generated Core starter pack");
            helper.assertTrue(dryRun == 1, "/ps reload --dry-run must stage without mutating live content");
            helper.assertTrue(diff == 1, "/ps diff must report the reviewed staged snapshot");
            helper.assertTrue(info == 1, "/ps info must inspect the starter definition and provenance");
            helper.assertTrue(lifecycle == 1, "/ps lifecycle selftest must prove Phase 4 transaction invariants");

            var player = makeMockPlayer(helper);
            var playerSource = player.createCommandSourceStack().withPermission(4);
            int initialGold = player.getInventory().countItem(Items.GOLD_INGOT);
            float initialMaxHealth = player.getMaxHealth();
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle status", playerSource) == 1,
                    "Lifecycle status must be available to an in-game operator"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle demo", playerSource) == 1,
                    "The visible lifecycle transaction must commit"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.GOLD_INGOT) == initialGold + 1,
                    "The transition action must deliver exactly one gold ingot"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 4,
                    "The persistent projector must add four max-health points"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle demo", playerSource) == 1,
                    "An exact lifecycle replay must return its cached success"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.GOLD_INGOT) == initialGold + 1,
                    "An idempotent replay must not deliver another item"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle recompute", playerSource) == 1,
                    "Persistent recompute must succeed"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.GOLD_INGOT) == initialGold + 1,
                    "Persistent recompute must not replay transition delivery"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle coowner", playerSource) == 1,
                    "A second source must be able to co-own the persistent value"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 4,
                    "The highest resolver must not stack two equal owners"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle revoke primary", playerSource) == 1,
                    "Primary source revocation must commit"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 4,
                    "Revoking one source must preserve the co-owner's value"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle revoke secondary", playerSource) == 1,
                    "Secondary source revocation must commit"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth,
                    "Revoking the final source must remove only the owned modifier"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle audit", playerSource) == 1,
                    "The in-game lifecycle audit must remain inspectable"
            );
            helper.succeed();
        } catch (CommandSyntaxException exception) {
            helper.fail("Phase 3 command execution failed: " + exception.getMessage());
        }
    }

    private static ServerPlayer makeMockPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "phase4-test-player"),
                false
        );
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
