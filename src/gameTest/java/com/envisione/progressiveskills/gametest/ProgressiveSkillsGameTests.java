package com.envisione.progressiveskills.gametest;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsDataSerializer;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.rule.BlockOrigin;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.server.offline.PendingOperationCoordinator;
import com.envisione.progressiveskills.server.offline.PendingOperationSavedData;
import com.envisione.progressiveskills.server.offline.PendingProgressionOperation;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.envisione.progressiveskills.server.transaction.PlayerPersistentProjector;
import com.envisione.progressiveskills.server.rule.RuleRuntime;
import com.envisione.progressiveskills.server.rule.BlockProvenanceSavedData;
import com.envisione.progressiveskills.server.tree.TreeRuntime;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Real-server proof for staged packs, transaction lifecycles, and Phase 5 persistence. */
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
            var context = TransactionRuntime.context(server).orElseThrow();
            var attached = player.getData(PsDataAttachments.PLAYER_DATA);
            var serialized = ProgressiveSkillsDataSerializer.encode(attached);
            var decoded = ProgressiveSkillsDataSerializer.decode(player.getUUID(), serialized);
            helper.assertTrue(decoded.active(), "A valid transaction attachment must decode as active");
            helper.assertTrue(
                    decoded.transactionState().stateRevision() == 1,
                    "The attachment must capture the committed transaction revision"
            );
            player.setData(PsDataAttachments.PLAYER_DATA, decoded);
            context.service().unloadAccount(player.getUUID());
            context.service().restoreAccount(player.getUUID(), decoded.transactionState());
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps lifecycle demo", playerSource) == 1,
                    "An exact lifecycle replay must survive attachment serialization and cache restoration"
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
            float initialJumpStrength = (float) player.getAttributeValue(Attributes.JUMP_STRENGTH);
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps skill get progressiveskills:physique", playerSource
                    ) == 1,
                    "The starter Physique skill must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps xp @s progressiveskills:physique 100", playerSource
                    ) == 1,
                    "A manual fixed point XP award must commit"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 2,
                    "Physique level one must add one heart"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps xp source @s progressiveskills:physique_training", playerSource
                    ) == 1,
                    "The stable custom Physique XP source must commit"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps xp @s progressiveskills:physique 850", playerSource
                    ) == 1,
                    "A level jump must commit all crossed highest level rewards"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 10,
                    "Discrete and scaling Physique health grants must resolve together"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH)
                            - initialJumpStrength * 1.15D) < 0.000001D,
                    "Physique level six must add the multiplied base jump grant"
            );
            var skillSnapshot = context.service().snapshot(player.getUUID());
            var physiqueId = ResourceLocation.fromNamespaceAndPath("progressiveskills", "physique");
            helper.assertTrue(
                    skillSnapshot.balances().get(SkillStateIds.activeXp(physiqueId)) == FixedPoint.parse("975"),
                    "Physique fixed point XP must equal the exact awarded total"
            );
            helper.assertTrue(
                    skillSnapshot.balances().get(SkillStateIds.level(physiqueId)) == 6,
                    "Physique level must derive from the exact curve boundary"
            );
            helper.assertTrue(
                    skillSnapshot.balances().get(ResourceLocation.fromNamespaceAndPath(
                            "progressiveskills", "global_points"
                    )) == 6,
                    "Lifetime highest level currency must award exactly once per crossed level"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps xp @s progressiveskills:physique -1", playerSource
                    ) == 0,
                    "Negative XP must fail closed"
            );
            var phase7Persisted = context.service().exportAccount(player.getUUID());
            PlayerPersistentProjector.clearKnownModifier(player);
            context.service().unloadAccount(player.getUUID());
            context.service().restoreAccount(player.getUUID(), phase7Persisted);
            helper.assertTrue(
                    context.service().forceReproject(player.getUUID(), context.projector()).successful(),
                    "Restored Physique ownership must reproject"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 10,
                    "Physique health grants must survive exact state restoration"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH)
                            - initialJumpStrength * 1.15D) < 0.000001D,
                    "Physique jump grants must survive exact state restoration"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps rule status", playerSource) == 1,
                    "The compiled Phase 9 rule table must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps rule preview progressiveskills:physique_stone_training", playerSource
                    ) == 1,
                    "The direct Phase 9 requirements and rounded amount must be previewable"
            );
            var rulePreview = RuleRuntime.preview(
                    player,
                    ResourceLocation.fromNamespaceAndPath(
                            "progressiveskills", "physique_stone_training"
                    )
            ).orElseThrow();
            helper.assertTrue(
                    rulePreview.requirementsPassed() && rulePreview.requirementsChecked() == 2
                            && rulePreview.dependencyCount() == 2
                            && rulePreview.amountUnits() == FixedPoint.parse("10")
                            && rulePreview.rounding().equals("floor"),
                    "The structured rule preview must match the hot path inputs. Preview " + rulePreview
            );
            var fakeResult = RuleRuntime.processBlockBreak(
                    FakePlayerFactory.getMinecraft(helper.getLevel()),
                    Blocks.STONE.defaultBlockState(),
                    helper.absolutePos(new BlockPos(0, 1, 1)),
                    helper.getLevel().dimension().location(),
                    helper.getLevel().getGameTime()
            );
            helper.assertTrue(
                    fakeResult.awardedUnits() == 0 && fakeResult.outcome().contains("fake player"),
                    "The default fake player policy must deny the matched route"
            );
            long beforeRuleXp = context.service().snapshot(player.getUUID()).balances().get(
                    SkillStateIds.activeXp(physiqueId)
            );
            BlockPos stone = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockSnapshot creativeSnapshot = BlockSnapshot.create(
                    helper.getLevel().dimension(), helper.getLevel(), stone
            );
            helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                    creativeSnapshot, Blocks.AIR.defaultBlockState(), player
            ));
            helper.assertTrue(
                    BlockProvenanceSavedData.get(server).originAt(
                            helper.getLevel().dimension().location(), stone
                    ) == BlockOrigin.CREATIVE_PLACED,
                    "The placement subscriber must record creative origin"
            );
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.teleportTo(stone.getX() + 0.5D, stone.getY() + 1.0D, stone.getZ() + 0.5D);
            helper.assertTrue(player.gameMode.destroyBlock(stone),
                    "The real server block break binding must accept stone");
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(
                            SkillStateIds.activeXp(physiqueId)
                    ) == beforeRuleXp + FixedPoint.parse("10"),
                    "The compiled stone rule must award its multiplied ten XP"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps explain xp last", playerSource) == 1,
                    "The last committed rule trace must be explainable"
            );
            var awardedTrace = RuleRuntime.lastTrace(player.getUUID()).orElseThrow();
            helper.assertTrue(
                    awardedTrace.requirementsChecked() == 2
                            && awardedTrace.preAntiAmountUnits() == FixedPoint.parse("10")
                            && awardedTrace.rounding().equals("floor"),
                    "The committed trace must retain requirements and pre anti exploit rounding evidence"
            );
            long eventTick = helper.getLevel().getGameTime();
            var duplicate = RuleRuntime.processBlockBreak(
                    player,
                    Blocks.STONE.defaultBlockState(),
                    stone,
                    helper.getLevel().dimension().location(),
                    eventTick
            );
            helper.assertTrue(
                    duplicate.awardedUnits() == 0 && duplicate.outcome().contains("duplicate"),
                    "The same block event token must be rejected without another award"
            );
            BlockPos secondStone = helper.absolutePos(new BlockPos(2, 1, 1));
            BlockSnapshot survivalSnapshot = BlockSnapshot.create(
                    helper.getLevel().dimension(), helper.getLevel(), secondStone
            );
            helper.getLevel().setBlockAndUpdate(secondStone, Blocks.STONE.defaultBlockState());
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                    survivalSnapshot, Blocks.AIR.defaultBlockState(), player
            ));
            BlockOrigin storedSurvivalOrigin = BlockProvenanceSavedData.get(server).originAt(
                    helper.getLevel().dimension().location(), secondStone
            );
            helper.assertTrue(
                    storedSurvivalOrigin == BlockOrigin.SURVIVAL_PLACED,
                    "The placement subscriber must record survival origin. Recorded "
                            + storedSurvivalOrigin.serializedName()
            );
            helper.assertTrue(player.gameMode.destroyBlock(secondStone),
                    "A survival placed stone block must be broken successfully"
            );
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(
                            SkillStateIds.activeXp(physiqueId)
                    ) == beforeRuleXp + FixedPoint.parse("10"),
                    "The starter origin policy must reject survival placed stone"
            );
            var survivalTrace = RuleRuntime.lastTrace(player.getUUID()).orElseThrow();
            helper.assertTrue(
                    survivalTrace.outcome().contains("survival_placed"),
                    "The rule trace must explain a survival placed origin rejection. Origin "
                            + survivalTrace.origin().serializedName() + ". Outcome " + survivalTrace.outcome()
            );
            BlockPos cooldownStone = helper.absolutePos(new BlockPos(3, 1, 1));
            helper.getLevel().setBlockAndUpdate(cooldownStone, Blocks.STONE.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(cooldownStone),
                    "A distinct natural stone block must be broken successfully"
            );
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(
                            SkillStateIds.activeXp(physiqueId)
                    ) == beforeRuleXp + FixedPoint.parse("10"),
                    "The stone cooldown must reject a distinct immediate natural event"
            );
            BlockPos firstLog = helper.absolutePos(new BlockPos(4, 1, 1));
            helper.getLevel().setBlockAndUpdate(firstLog, Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(firstLog),
                    "The first time tag matched log rule must bind to a real break event");
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(
                            SkillStateIds.activeXp(physiqueId)
                    ) == beforeRuleXp + FixedPoint.parse("30"),
                    "The first log must award exactly twenty XP"
            );
            BlockPos secondLog = helper.absolutePos(new BlockPos(5, 1, 1));
            helper.getLevel().setBlockAndUpdate(secondLog, Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(secondLog),
                    "The second log must still be physically breakable");
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(
                            SkillStateIds.activeXp(physiqueId)
                    ) == beforeRuleXp + FixedPoint.parse("30"),
                    "Persistent first time memory must reject the second log award"
            );
            RuleRuntime.pause();
            var paused = RuleRuntime.processBlockBreak(
                    player,
                    Blocks.STONE.defaultBlockState(),
                    helper.absolutePos(new BlockPos(6, 1, 1)),
                    helper.getLevel().dimension().location(),
                    helper.getLevel().getGameTime()
            );
            helper.assertTrue(
                    paused.awardedUnits() == 0 && paused.outcome().contains("no active"),
                    "The publication barrier must pause every gameplay route"
            );
            RuleRuntime.reload(server);
            BlockPos thirdLog = helper.absolutePos(new BlockPos(7, 1, 1));
            helper.getLevel().setBlockAndUpdate(thirdLog, Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(thirdLog),
                    "The rebuilt tag route must remain physically callable");
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(
                            SkillStateIds.activeXp(physiqueId)
                    ) == beforeRuleXp + FixedPoint.parse("30"),
                    "A route table rebuild must retain first time source memory"
            );

            var treeId = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "physique_training"
            );
            var conditioningId = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "physique_training/conditioning"
            );
            var resilienceId = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "physique_training/resilience"
            );
            var pointsId = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "global_points"
            );
            double treeHealthBefore = player.getAttributeValue(Attributes.MAX_HEALTH);
            double treeArmorBefore = player.getAttributeValue(Attributes.ARMOR);
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps tree list", playerSource) == 1,
                    "The live Core tree catalog must be listed"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps tree info " + treeId, playerSource
                    ) == 1,
                    "The starter Physique tree must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps tree preview buy " + treeId + " " + conditioningId, playerSource
                    ) == 1,
                    "The first tree node must preview from authoritative state"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps tree buy " + treeId + " " + conditioningId, playerSource
                    ) == 1,
                    "The first tree node purchase must commit"
            );
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(pointsId) == 5,
                    "The first tree purchase must debit exactly one point"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.MAX_HEALTH) - treeHealthBefore - 1.0D)
                            < 0.000001D,
                    "The Conditioning source must add exactly one max health point"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps tree buy " + treeId + " " + conditioningId, playerSource
                    ) == 0,
                    "A purchased node must not charge or grant twice"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps tree buy " + treeId + " " + resilienceId, playerSource
                    ) == 1,
                    "A dependent tree node purchase must commit"
            );
            var purchasedTreeState = context.service().snapshot(player.getUUID());
            helper.assertTrue(
                    purchasedTreeState.balances().get(pointsId) == 3
                            && purchasedTreeState.paidCosts().size() == 2,
                    "Both purchases and their exact paid records must commit together"
            );
            helper.assertTrue(
                    player.getData(PsDataAttachments.PLAYER_DATA).transactionState().paidCosts().size() == 2,
                    "Paid tree records must be present in the persisted player attachment"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.ARMOR) - treeArmorBefore - 1.0D)
                            < 0.000001D,
                    "The Resilience source must add exactly one armor point"
            );
            var treeRefundPreview = TreeRuntime.previewRefund(player, treeId, conditioningId);
            helper.assertTrue(
                    treeRefundPreview.affectedNodes().equals(List.of(resilienceId, conditioningId))
                            && treeRefundPreview.refundBalances().get(pointsId) == 3,
                    "Cascade preview must order the dependent first and total historical payments"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "ps tree refund " + treeId + " " + conditioningId + " "
                                    + treeRefundPreview.digest(),
                            playerSource
                    ) == 1,
                    "The reviewed cascade refund must commit once"
            );
            var refundedTreeState = context.service().snapshot(player.getUUID());
            helper.assertTrue(
                    refundedTreeState.balances().get(pointsId) == 6
                            && refundedTreeState.paidCosts().isEmpty(),
                    "Cascade refund must restore exact historical payments and clear paid records"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.MAX_HEALTH) - treeHealthBefore)
                            < 0.000001D
                            && Math.abs(player.getAttributeValue(Attributes.ARMOR) - treeArmorBefore)
                            < 0.000001D,
                    "Cascade refund must remove only the two tree owned grants"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps persistence status", playerSource) == 1,
                    "The in-game persistence status must inspect the active attachment"
            );

            var currentDefinition = TransactionRuntime.currentDefinition().orElseThrow();
            UUID offlineOperationId = UUID.randomUUID();
            Instant createdAt = Instant.now();
            var offlineOperation = PendingProgressionOperation.pending(
                    offlineOperationId,
                    player.getUUID(),
                    player.getUUID(),
                    createdAt,
                    createdAt.plusSeconds(300),
                    currentDefinition,
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "phase5_offline_points"),
                    2,
                    0,
                    10,
                    false
            );
            var pendingStore = PendingOperationSavedData.get(server);
            pendingStore.queue(offlineOperation);
            UUID firstLoginAttempt = UUID.randomUUID();
            PendingOperationCoordinator.applyOnLogin(player, context, firstLoginAttempt);
            helper.assertTrue(
                    player.getData(PsDataAttachments.PLAYER_DATA).operationReceipt(offlineOperationId).isPresent(),
                    "Offline application must write a same-attachment operation receipt"
            );
            helper.assertTrue(
                    pendingStore.pendingFor(player.getUUID()).size() == 1,
                    "SavedData must retain the operation until a later login proves the receipt survived"
            );
            PendingOperationCoordinator.applyOnLogin(player, context, firstLoginAttempt);
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(offlineOperation.balanceId()) == 2,
                    "A duplicate login callback must not apply the offline balance twice"
            );

            var reloadedAttachment = ProgressiveSkillsDataSerializer.decode(
                    player.getUUID(),
                    ProgressiveSkillsDataSerializer.encode(player.getData(PsDataAttachments.PLAYER_DATA))
            );
            player.setData(PsDataAttachments.PLAYER_DATA, reloadedAttachment);
            context.service().unloadAccount(player.getUUID());
            context.service().restoreAccount(player.getUUID(), reloadedAttachment.transactionState());
            PendingOperationCoordinator.applyOnLogin(player, context, UUID.randomUUID());
            helper.assertTrue(
                    pendingStore.pendingFor(player.getUUID()).isEmpty(),
                    "A later attachment load with the receipt must consume the SavedData operation"
            );
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).balances().get(offlineOperation.balanceId()) == 2,
                    "Offline operation consumption must preserve its exactly-once balance"
            );

            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps persistence snapshot", playerSource) == 1,
                    "The binary persistence snapshot must write and verify"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("ps persistence export", playerSource) == 1,
                    "The readable persistence export must write within its bound"
            );

            var endReturn = makeReplacement(helper, player);
            endReturn.copyAttachmentsFrom(player, false);
            helper.assertTrue(
                    endReturn.getData(PsDataAttachments.PLAYER_DATA).transactionState()
                            .equals(player.getData(PsDataAttachments.PLAYER_DATA).transactionState()),
                    "A non-death replacement must copy attachment state without creating a death operation"
            );
            helper.assertTrue(
                    endReturn.getData(PsDataAttachments.PLAYER_DATA).view().deathMarker().isEmpty(),
                    "An End-return replacement must have no death marker"
            );

            UUID deathId = UUID.randomUUID();
            player.getData(PsDataAttachments.PLAYER_DATA)
                    .prepareDeath(deathId, Instant.now(), false);
            var deathReplacement = makeReplacement(helper, player);
            deathReplacement.copyAttachmentsFrom(player, true);
            var deathData = deathReplacement.getData(PsDataAttachments.PLAYER_DATA);
            helper.assertTrue(
                    deathData.transactionState().equals(
                            player.getData(PsDataAttachments.PLAYER_DATA).transactionState()),
                    "copyOnDeath must preserve exact transaction state and permanent receipts"
            );
            helper.assertTrue(
                    deathData.completeDeath(Instant.now()).isPresent()
                            && deathData.operationReceipt(deathId).isPresent(),
                    "Death completion must atomically materialize its same-attachment receipt"
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

    private static ServerPlayer makeReplacement(GameTestHelper helper, ServerPlayer original) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                original.getGameProfile(),
                original.clientInformation()
        );
    }
}
