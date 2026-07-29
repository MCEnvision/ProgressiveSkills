package com.envisione.progressiveskills.gametest;

import com.envisione.progressiveskills.ProjectIdentity;
import com.envisione.progressiveskills.common.classdef.ClassGrantType;
import com.envisione.progressiveskills.common.classdef.ClassProgression;
import com.envisione.progressiveskills.common.carrier.CarrierCatalog;
import com.envisione.progressiveskills.common.creator.BuildShareCode;
import com.envisione.progressiveskills.common.ability.AbilityFlagEffect;
import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.common.ability.AbilityTargetMode;
import com.envisione.progressiveskills.common.ability.AbilityTargeting;
import com.envisione.progressiveskills.common.data.ProgressiveSkillsDataSerializer;
import com.envisione.progressiveskills.common.data.PsDataAttachments;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.rule.BlockOrigin;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import com.envisione.progressiveskills.common.tree.TreeCatalog;
import com.envisione.progressiveskills.common.transaction.CascadePlan;
import com.envisione.progressiveskills.common.transaction.EntitlementKey;
import com.envisione.progressiveskills.common.transaction.EntitlementMutation;
import com.envisione.progressiveskills.common.transaction.EntitlementResolver;
import com.envisione.progressiveskills.common.transaction.GrantSourceId;
import com.envisione.progressiveskills.common.transaction.IdempotencyKey;
import com.envisione.progressiveskills.common.transaction.ProgressionCause;
import com.envisione.progressiveskills.common.transaction.TransactionPlan;
import com.envisione.progressiveskills.common.transaction.TransactionStep;
import com.envisione.progressiveskills.server.classruntime.ClassRuntime;
import com.envisione.progressiveskills.server.carrier.BehaviorArchiveSavedData;
import com.envisione.progressiveskills.server.ability.AbilityRuntime;
import com.envisione.progressiveskills.server.ability.AbilityTargetResolver;
import com.envisione.progressiveskills.server.offline.PendingOperationCoordinator;
import com.envisione.progressiveskills.server.offline.PendingOperationSavedData;
import com.envisione.progressiveskills.server.offline.PendingProgressionOperation;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.envisione.progressiveskills.server.transaction.PlayerPersistentProjector;
import com.envisione.progressiveskills.server.rule.RuleRuntime;
import com.envisione.progressiveskills.server.rule.BlockProvenanceSavedData;
import com.envisione.progressiveskills.server.pack.PackRuntime;
import com.envisione.progressiveskills.server.provider.ProviderRuntime;
import com.envisione.progressiveskills.server.social.MultiplayerSavedData;
import com.envisione.progressiveskills.server.social.TeamSavedData;
import com.envisione.progressiveskills.server.studio.StudioService;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.Map;
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
            helper.assertTrue(
                    server.getCommands().getDispatcher().getRoot().getChild("pskills") != null,
                    "/pskills must be the registered ProgressiveSkills command root"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().getRoot().getChild("ps") == null,
                    "/ps must remain available for other mods"
            );
            int status = server.getCommands().getDispatcher().execute("pskills status", source);
            int validate = server.getCommands().getDispatcher().execute("pskills validate", source);
            int dryRun = server.getCommands().getDispatcher().execute("pskills reload --dry-run", source);
            int diff = server.getCommands().getDispatcher().execute("pskills diff", source);
            int info = server.getCommands().getDispatcher().execute(
                    "pskills info progressiveskills:component_spec progressiveskills:engine_name --provenance",
                    source
            );
            int lifecycle = server.getCommands().getDispatcher().execute("pskills lifecycle selftest", source);
            helper.assertTrue(status == 1, "/pskills status must report a live Phase 3 generation");
            helper.assertTrue(validate == 1, "/pskills validate must accept the generated Core starter pack");
            helper.assertTrue(dryRun == 1, "/pskills reload --dry-run must stage without mutating live content");
            helper.assertTrue(diff == 1, "/pskills diff must report the reviewed staged snapshot");
            helper.assertTrue(info == 1, "/pskills info must inspect the starter definition and provenance");
            helper.assertTrue(lifecycle == 1, "/pskills lifecycle selftest must prove Phase 4 transaction invariants");

            var player = makeMockPlayer(helper);
            var playerSource = player.createCommandSourceStack().withPermission(4);
            int initialGold = player.getInventory().countItem(Items.GOLD_INGOT);
            float initialMaxHealth = player.getMaxHealth();
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle status", playerSource) == 1,
                    "Lifecycle status must be available to an in-game operator"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle demo", playerSource) == 1,
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
                    server.getCommands().getDispatcher().execute("pskills lifecycle demo", playerSource) == 1,
                    "An exact lifecycle replay must survive attachment serialization and cache restoration"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.GOLD_INGOT) == initialGold + 1,
                    "An idempotent replay must not deliver another item"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle recompute", playerSource) == 1,
                    "Persistent recompute must succeed"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.GOLD_INGOT) == initialGold + 1,
                    "Persistent recompute must not replay transition delivery"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle coowner", playerSource) == 1,
                    "A second source must be able to co-own the persistent value"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 4,
                    "The highest resolver must not stack two equal owners"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle revoke primary", playerSource) == 1,
                    "Primary source revocation must commit"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 4,
                    "Revoking one source must preserve the co-owner's value"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle revoke secondary", playerSource) == 1,
                    "Secondary source revocation must commit"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth,
                    "Revoking the final source must remove only the owned modifier"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills lifecycle audit", playerSource) == 1,
                    "The in-game lifecycle audit must remain inspectable"
            );
            float initialJumpStrength = (float) player.getAttributeValue(Attributes.JUMP_STRENGTH);
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills skill get progressiveskills:physique", playerSource
                    ) == 1,
                    "The starter Physique skill must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills xp @s progressiveskills:physique 100", playerSource
                    ) == 1,
                    "A manual fixed point XP award must commit"
            );
            helper.assertTrue(
                    player.getMaxHealth() == initialMaxHealth + 2,
                    "Physique level one must add one heart"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills xp source @s progressiveskills:physique_training", playerSource
                    ) == 1,
                    "The stable custom Physique XP source must commit"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills xp @s progressiveskills:physique 850", playerSource
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
                            "pskills xp @s progressiveskills:physique -1", playerSource
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
                    server.getCommands().getDispatcher().execute("pskills rule status", playerSource) == 1,
                    "The compiled Phase 9 rule table must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills rule preview progressiveskills:physique_stone_training", playerSource
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
                    server.getCommands().getDispatcher().execute("pskills explain xp last", playerSource) == 1,
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
                    server.getCommands().getDispatcher().execute("pskills tree list", playerSource) == 15,
                    "The live Core tree catalog must list all fifteen starter trees"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills tree info " + treeId, playerSource
                    ) == 1,
                    "The starter Physique tree must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills tree preview buy " + treeId + " " + conditioningId, playerSource
                    ) == 1,
                    "The first tree node must preview from authoritative state"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills tree buy " + treeId + " " + conditioningId, playerSource
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
                            "pskills tree buy " + treeId + " " + conditioningId, playerSource
                    ) == 0,
                    "A purchased node must not charge or grant twice"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills tree buy " + treeId + " " + resilienceId, playerSource
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
                            "pskills tree refund " + treeId + " " + conditioningId + " "
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

            var warriorId = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "warrior"
            );
            var scholarId = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "scholar"
            );
            var warriorGuard = new EntitlementKey(
                    ClassGrantType.ABILITY.entitlementType().orElseThrow(),
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "warrior_guard")
            );
            var scholarStage = new EntitlementKey(
                    ClassGrantType.STAGE.entitlementType().orElseThrow(),
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "scholar_training")
            );
            var trainingAccess = new EntitlementKey(
                    ClassGrantType.TREE_ACCESS.entitlementType().orElseThrow(), treeId
            );
            var combatInsight = new EntitlementKey(
                    ClassGrantType.ABILITY.entitlementType().orElseThrow(),
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "combat_insight")
            );
            int initialWoodenSwords = player.getInventory().countItem(Items.WOODEN_SWORD);
            int initialBooks = player.getInventory().countItem(Items.BOOK);
            double classAttackBefore = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills class list", playerSource) == 2,
                    "The live Core class catalog must list both starter classes"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class info " + warriorId, playerSource
                    ) == 1,
                    "The starter Warrior class must be inspectable"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class preview select " + warriorId, playerSource
                    ) == 1,
                    "Warrior selection must preview from authoritative state"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class select " + warriorId, playerSource
                    ) == 1,
                    "Warrior selection must commit through the class command route"
            );
            var warriorState = context.service().snapshot(player.getUUID());
            helper.assertTrue(
                    warriorState.balances().get(pointsId) == 5,
                    "Warrior selection must sink exactly one global point"
            );
            helper.assertTrue(
                    ClassProgression.selectedClasses(warriorState).equals(java.util.Set.of(warriorId))
                            && ClassProgression.activeClasses(warriorState).equals(java.util.Set.of(warriorId)),
                    "Warrior must occupy one weighted slot and become active"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                            - classAttackBefore - 1.0D) < 0.000001D,
                    "The active Warrior source must add one attack damage"
            );
            helper.assertTrue(
                    warriorState.projectedValues().getOrDefault(warriorGuard, 0L) == 1,
                    "The active Warrior source must own its guard ability"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.WOODEN_SWORD) == initialWoodenSwords + 1,
                    "Warrior selection must deliver one starter sword"
            );
            helper.assertTrue(
                    classKitReceipts(player) == 1,
                    "Warrior selection must persist one character scoped starter kit receipt"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class preview select " + scholarId, playerSource
                    ) == 1,
                    "Scholar selection must fit the remaining weighted capacity"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class select " + scholarId, playerSource
                    ) == 1,
                    "Scholar selection must commit through the class command route"
            );
            var combinedClassState = context.service().snapshot(player.getUUID());
            helper.assertTrue(
                    combinedClassState.balances().get(pointsId) == 4,
                    "Scholar selection must sink exactly one additional global point"
            );
            helper.assertTrue(
                    ClassProgression.selectedClasses(combinedClassState).equals(
                            java.util.Set.of(warriorId, scholarId)
                    ) && ClassProgression.activeClasses(combinedClassState).equals(
                            java.util.Set.of(warriorId, scholarId)
                    ),
                    "Both unit weight classes must remain active in capacity two"
            );
            helper.assertTrue(
                    combinedClassState.projectedValues().getOrDefault(scholarStage, 0L) == 1
                            && combinedClassState.projectedValues().getOrDefault(trainingAccess, 0L) == 1,
                    "Scholar must own its stage and tree access grants"
            );
            helper.assertTrue(
                    combinedClassState.projectedValues().getOrDefault(combatInsight, 0L) == 1,
                    "Warrior and Scholar must activate the Student of War synergy"
            );
            helper.assertTrue(
                    player.getInventory().countItem(Items.BOOK) == initialBooks + 1
                            && classKitReceipts(player) == 2,
                    "Scholar selection must deliver and receipt one starter book"
            );
            helper.assertTrue(
                    classPaidRecords(combinedClassState) == 2,
                    "Both positive class selection costs must retain exact paid evidence"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class entitlements", playerSource
                    ) == 1,
                    "Class ownership must be inspectable through the command route"
            );

            var stageCoowner = new GrantSourceId(
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "manual"),
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "phase11_gametest"),
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "phase11_gametest/scholar_stage")
            );
            helper.assertTrue(
                    grantLogicalEntitlement(context, player, scholarStage, stageCoowner),
                    "A second source must be able to coown the Scholar stage"
            );
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).ownership().get(scholarStage).size() == 2,
                    "The Scholar stage must retain both source identities"
            );
            var classRespecPreview = ClassRuntime.previewRespec(player, scholarId);
            helper.assertTrue(
                    classRespecPreview.allowed()
                            && classRespecPreview.costBalances().get(pointsId) == 1,
                    "Scholar respec must require its configured one point cost"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class preview respec " + scholarId, playerSource
                    ) == 1,
                    "Scholar respec must be previewable through the class command route"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class respec " + scholarId + " " + classRespecPreview.digest(),
                            playerSource
                    ) == 1,
                    "The reviewed Scholar respec must commit through the class command route"
            );
            var respeccedClassState = context.service().snapshot(player.getUUID());
            helper.assertTrue(
                    respeccedClassState.balances().get(pointsId) == 3,
                    "Respec must sink its cost without refunding the Scholar selection payment"
            );
            helper.assertTrue(
                    ClassProgression.selectedClasses(respeccedClassState).equals(java.util.Set.of(warriorId))
                            && ClassProgression.activeClasses(respeccedClassState).equals(java.util.Set.of(warriorId)),
                    "Scholar respec must preserve the selected and active Warrior"
            );
            helper.assertTrue(
                    respeccedClassState.projectedValues().getOrDefault(scholarStage, 0L) == 1
                            && respeccedClassState.ownership().get(scholarStage).size() == 1
                            && respeccedClassState.ownership().get(scholarStage).containsKey(stageCoowner),
                    "Scholar respec must preserve the independent stage coowner"
            );
            helper.assertTrue(
                    respeccedClassState.projectedValues().getOrDefault(trainingAccess, 0L) == 0
                            && respeccedClassState.projectedValues().getOrDefault(combatInsight, 0L) == 0,
                    "Scholar respec must revoke only its tree access and synergy sources"
            );
            helper.assertTrue(
                    classPaidRecords(respeccedClassState) == 1
                            && classKitReceipts(player) == 2
                            && player.getInventory().countItem(Items.BOOK) == initialBooks + 1,
                    "Respec must remove paid selection evidence while retaining permanent kit receipts"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills class select " + scholarId, playerSource
                    ) == 1,
                    "Scholar must be selectable again after respec"
            );
            var reselectedClassState = context.service().snapshot(player.getUUID());
            helper.assertTrue(
                    reselectedClassState.balances().get(pointsId) == 2
                            && classPaidRecords(reselectedClassState) == 2,
                    "Scholar reselection must sink a new selection cost and restore paid evidence"
            );
            helper.assertTrue(
                    ClassProgression.activeClasses(reselectedClassState).equals(
                            java.util.Set.of(warriorId, scholarId)
                    ) && reselectedClassState.projectedValues().getOrDefault(combatInsight, 0L) == 1,
                    "Scholar reselection must restore both active classes and their synergy"
            );
            helper.assertTrue(
                    reselectedClassState.ownership().get(scholarStage).size() == 2
                            && classKitReceipts(player) == 2
                            && player.getInventory().countItem(Items.BOOK) == initialBooks + 1,
                    "Scholar reselection must restore coownership without repeating its starter kit"
            );

            var secondWind = ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "second_wind");
            var combatAware = new EntitlementKey(
                    AbilityFlagEffect.FLAG_ENTITLEMENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "combat_aware")
            );
            var abilityState = AbilityRuntime.state(player);
            helper.assertTrue(
                    abilityState.ownedAbilities().equals(java.util.Set.of(
                            warriorGuard.targetId(), combatInsight.targetId(), secondWind)),
                    "Class and synergy sources must expose all three starter abilities"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills ability list", playerSource) == 3,
                    "The live Core ability catalog must list all three starter abilities"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability info " + secondWind, playerSource) == 1,
                    "Second Wind must be inspectable through the ability command route"
            );
            helper.assertTrue(
                    context.service().snapshot(player.getUUID()).projectedValues()
                            .getOrDefault(combatAware, 0L) == 1,
                    "The owned passive ability must project its source owned flag"
            );
            double armorBeforeGuard = player.getAttributeValue(Attributes.ARMOR);
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability assign " + warriorGuard.targetId() + " 1", playerSource) == 1,
                    "Warrior Guard must assign to the first fixed ability slot"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability toggle " + warriorGuard.targetId(), playerSource) == 1,
                    "Warrior Guard must toggle on through the authoritative planner"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.ARMOR) - armorBeforeGuard - 2.0D)
                            < 0.000001D,
                    "The enabled guard toggle must project two armor"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability toggle " + warriorGuard.targetId(), playerSource) == 1,
                    "Warrior Guard must toggle off through the same planner"
            );
            helper.assertTrue(
                    Math.abs(player.getAttributeValue(Attributes.ARMOR) - armorBeforeGuard) < 0.000001D,
                    "Disabling the guard toggle must remove only its owned armor source"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability assign " + secondWind + " 2", playerSource) == 1,
                    "Second Wind must assign to the second fixed ability slot"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability select 2", playerSource) == 1,
                    "The second fixed ability slot must become selected"
            );
            player.getFoodData().setFoodLevel(20);
            player.setHealth(Math.max(1.0F, player.getMaxHealth() - 8.0F));
            float healthBeforeAbility = player.getHealth();
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills ability activate 2", playerSource) == 1,
                    "Second Wind must activate on a valid self target"
            );
            helper.assertTrue(
                    player.getFoodData().getFoodLevel() == 16,
                    "Second Wind must consume exactly four hunger"
            );
            helper.assertTrue(
                    Math.abs(player.getHealth() - healthBeforeAbility - 4.0F) < 0.000001F,
                    "Second Wind must heal exactly four health"
            );
            var speed = BuiltInRegistries.MOB_EFFECT.getHolder(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "speed")).orElseThrow();
            helper.assertTrue(player.hasEffect(speed), "Second Wind must apply its bounded speed effect");
            AbilityState activatedAbilities = AbilityRuntime.state(player);
            helper.assertTrue(
                    activatedAbilities.selectedSlot().orElseThrow().equals(AbilityState.slotId(1))
                            && activatedAbilities.charges().get(secondWind).current() == 1
                            && activatedAbilities.charges().get(secondWind).maximum() == 2
                            && activatedAbilities.cooldownRemaining(
                            ResourceLocation.fromNamespaceAndPath("progressiveskills", "recovery"),
                            player.serverLevel().getGameTime()) > 0,
                    "Ability selection and cooldown state must remain authoritative after activation"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute(
                            "pskills ability activate 2", playerSource) == 0
                            && player.getFoodData().getFoodLevel() == 16,
                    "Cooldown rejection must happen before another vanilla cost is consumed"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills ability status", playerSource) == 1,
                    "Ability slots and runtime state must remain inspectable through chat"
            );
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            player.setYHeadRot(0.0F);
            var entityTarget = makeMockPlayer(helper);
            entityTarget.setPos(player.getX(), player.getY(), player.getZ() + 4.0D);
            var entityResolution = AbilityTargetResolver.resolve(
                    player, new AbilityTargeting(AbilityTargetMode.ENTITY, 8, true));
            helper.assertTrue(
                    entityResolution.accepted()
                            && entityResolution.target().orElseThrow().entityId().orElseThrow()
                            .equals(entityTarget.getUUID()),
                    "Entity targeting must resolve the server ray and line of sight"
            );
            entityTarget.setPos(player.getX() + 16.0D, player.getY(), player.getZ());
            BlockPos abilityBlock = BlockPos.containing(
                    player.getX(), player.getEyeY(), player.getZ() + 4.0D);
            player.serverLevel().setBlockAndUpdate(abilityBlock, Blocks.STONE.defaultBlockState());
            var blockResolution = AbilityTargetResolver.resolve(
                    player, new AbilityTargeting(AbilityTargetMode.BLOCK, 8, true));
            helper.assertTrue(
                    blockResolution.accepted()
                            && blockResolution.target().orElseThrow().blockPos().orElseThrow()
                            .equals(abilityBlock),
                    "Block targeting must resolve the server ray and line of sight"
            );
            helper.assertTrue(
                    !AbilityTargetResolver.resolve(
                            player, new AbilityTargeting(AbilityTargetMode.BLOCK, 2, true)).accepted(),
                    "Targets outside the configured range must fail before activation"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills persistence status", playerSource) == 1,
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
                    server.getCommands().getDispatcher().execute("pskills persistence snapshot", playerSource) == 1,
                    "The binary persistence snapshot must write and verify"
            );
            helper.assertTrue(
                    server.getCommands().getDispatcher().execute("pskills persistence export", playerSource) == 1,
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void latePhaseInfrastructure(GameTestHelper helper) {
        try {
            var server = helper.getLevel().getServer();
            var pack = PackRuntime.service().orElseThrow();
            var canonical = pack.live().snapshot().canonicalIr();
            SkillCatalog skills = SkillCatalog.from(canonical);
            TreeCatalog trees = TreeCatalog.from(canonical, skills);
            CarrierCatalog carriers = CarrierCatalog.from(canonical, skills, trees);
            var archive = BehaviorArchiveSavedData.get(server);
            helper.assertTrue(!carriers.carriers().isEmpty(),
                    "The cumulative starter pack must contain carrier definitions");
            helper.assertTrue(archive.verify().valid()
                            && archive.status().entries() >= carriers.behaviors().size(),
                    "Pinned carrier behaviors must be reserved and verifiable");
            helper.assertTrue(ProviderRuntime.registry().isPresent(),
                    "The provider registry must be active without optional mods");
            var source = server.createCommandSourceStack().withPermission(4);
            helper.assertTrue(server.getCommands().getDispatcher().execute("pskills doctor", source) == 1,
                    "The consolidated doctor must report healthy late phase services");
            helper.assertTrue(server.getCommands().getDispatcher().execute("pskills diagnose", source) == 1,
                    "Provider diagnostics must remain available through chat");

            UUID partyOwner = UUID.randomUUID();
            UUID partyMember = UUID.randomUUID();
            MultiplayerSavedData social = MultiplayerSavedData.get(server);
            social.createParty(partyOwner, "Game Test Party");
            social.invite(partyOwner, partyMember);
            social.acceptInvite(partyMember);
            var receipt = social.shareExact(
                    partyOwner,
                    ResourceLocation.fromNamespaceAndPath("progressiveskills", "physique"),
                    Map.of(partyOwner, 1L, partyMember, 1L),
                    "Game test contribution allocation");
            social.markContributionDelivered(partyOwner, receipt.receiptId(), partyOwner);
            social.markContributionDelivered(partyOwner, receipt.receiptId(), partyMember);
            social.completeContribution(partyOwner, receipt.receiptId());
            helper.assertTrue(social.receipt(receipt.receiptId()).isPresent(),
                    "Completed contribution receipts must remain inspectable");

            UUID teamOwner = UUID.randomUUID();
            UUID teamMember = UUID.randomUUID();
            TeamSavedData teams = TeamSavedData.get(server);
            teams.create(teamOwner, ResourceLocation.fromNamespaceAndPath(
                    "progressiveskills", "gametest_team_" + teamOwner.toString().replace("-", "")));
            teams.invite(teamOwner, teamMember);
            teams.accept(teamMember);
            teams.transferOwnership(teamOwner, teamMember);
            helper.assertTrue(teams.team(teamOwner).orElseThrow().owner().equals(teamMember),
                    "Team ownership changes must use authoritative saved state");

            UUID studioOwner = UUID.randomUUID();
            var draft = StudioService.create(server, studioOwner, "gametest", "Game Test Draft");
            var edited = StudioService.write(
                    server, draft.id(), studioOwner, draft.revision(), "variables/value.json",
                    "{\"schema_version\":2,\"variable\":{\"id\":\"gametest:value\",\"value\":1}}");
            helper.assertTrue(StudioService.lint(server, draft.id()).valid()
                            && StudioService.history(server, draft.id()).equals(List.of(0L)),
                    "Studio edits must create a valid restorable history revision");
            var rebased = StudioService.rebase(
                    server, draft.id(), studioOwner, edited.revision());
            var restored = StudioService.restoreHistory(
                    server, draft.id(), studioOwner, rebased.revision(), 0L);
            helper.assertTrue(restored.revision() == 3
                            && StudioService.lint(server, draft.id()).valid(),
                    "Studio rebase and history restore must preserve a valid draft");

            var build = new BuildShareCode.Build(
                    pack.live().snapshot().contentDigest(), List.of(), List.of(), Map.of());
            helper.assertTrue(BuildShareCode.decode(BuildShareCode.encode(build)).equals(build),
                    "Build sharing must round trip with a canonical checksum");
            helper.succeed();
        } catch (Exception exception) {
            helper.fail("Late phase infrastructure failed. "
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage()));
        }
    }

    private static ServerPlayer makeMockPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        UUID playerId = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(playerId, "ps-" + playerId.toString().substring(0, 8)),
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

    private static long classKitReceipts(ServerPlayer player) {
        return player.getData(PsDataAttachments.PLAYER_DATA).transactionState().receipts().keySet().stream()
                .filter(key -> key.source().ownerKind().equals(DefinitionKinds.CLASS.id()))
                .count();
    }

    private static long classPaidRecords(
            com.envisione.progressiveskills.common.transaction.ProgressionSnapshot snapshot
    ) {
        return snapshot.paidCosts().keySet().stream()
                .filter(instance -> instance.ownerKind().equals(DefinitionKinds.CLASS.id()))
                .count();
    }

    private static boolean grantLogicalEntitlement(
            TransactionRuntime.Context context,
            ServerPlayer player,
            EntitlementKey key,
            GrantSourceId source
    ) {
        var snapshot = context.service().snapshot(player.getUUID());
        var step = new TransactionStep(
                ResourceLocation.fromNamespaceAndPath("progressiveskills", "phase11_gametest"),
                List.of(),
                List.of(EntitlementMutation.grant(key, source, 1, EntitlementResolver.BOOLEAN_UNION)),
                List.of()
        );
        var plan = CascadePlan.single(new TransactionPlan(
                player.getUUID(),
                player.getUUID(),
                new IdempotencyKey("phase11/gametest/coowner/" + UUID.randomUUID()),
                snapshot.stateRevision(),
                TransactionRuntime.currentDefinition().orElseThrow(),
                ProgressionCause.ADMIN,
                "Phase 11 class coowner proof",
                step
        ));
        return context.executeAndPersist(
                player, plan, TransactionRuntime.currentDefinition().orElseThrow()
        ).status().committed();
    }
}
