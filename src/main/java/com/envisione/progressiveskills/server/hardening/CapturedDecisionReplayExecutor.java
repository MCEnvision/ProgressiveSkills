package com.envisione.progressiveskills.server.hardening;

import com.envisione.progressiveskills.common.ability.AbilityState;
import com.envisione.progressiveskills.server.ability.AbilityRuntime;
import com.envisione.progressiveskills.server.classruntime.ClassRuntime;
import com.envisione.progressiveskills.server.creator.CreatorRuntime;
import com.envisione.progressiveskills.server.skill.SkillRuntime;
import com.envisione.progressiveskills.server.social.CombatContributionProfiles;
import com.envisione.progressiveskills.server.social.PvpAwardLedgerSavedData;
import com.envisione.progressiveskills.server.social.SocialProviderRuntime;
import com.envisione.progressiveskills.server.transaction.TransactionRuntime;
import com.envisione.progressiveskills.server.tree.TreeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class CapturedDecisionReplayExecutor implements ReproductionReplayService.DecisionExecutor {
    public static void capture(
            Map<String, Long> state,
            ServerPlayer player,
            List<DecisionTraceRuntime.Decision> decisions
    ) {
        state.put("replay.data_active", TransactionRuntime.context(player.getServer())
                .filter(value -> value.ready(player)).isPresent() ? 1L : 0L);
        for (DecisionTraceRuntime.Decision decision : decisions) {
            String prefix = statePrefix(decision.category(), decision.subject());
            Facts facts = facts(player, decision.category(), decision.subject());
            state.put(prefix + ".known", facts.known() ? 1L : 0L);
            state.put(prefix + ".eligible", facts.eligible() ? 1L : 0L);
        }
    }

    @Override
    public ReproductionReplayService.DecisionOutcome evaluate(
            ReproductionReplayService.ReplayDecision decision,
            Map<String, Long> capturedState
    ) {
        String prefix = statePrefix(decision.type(), decision.subject());
        boolean active = capturedState.getOrDefault("replay.data_active", 0L) == 1L;
        boolean known = capturedState.getOrDefault(prefix + ".known", 0L) == 1L;
        boolean eligible = capturedState.getOrDefault(prefix + ".eligible", 0L) == 1L;
        boolean allowed = active && known && eligible;
        return new ReproductionReplayService.DecisionOutcome(
                allowed,
                allowed
                        ? "Captured progression snapshot accepts the decision"
                        : "Captured progression snapshot rejects the decision",
                decision.capturedStateRevision());
    }

    private static Facts facts(ServerPlayer player, String type, String subject) {
        try {
            return switch (type) {
                case "xp", "award" -> {
                    ResourceLocation id = requiredId(subject);
                    boolean known = SkillRuntime.catalog().flatMap(value -> value.skill(id)).isPresent();
                    yield new Facts(known, known);
                }
                case "ability" -> ability(player, subject);
                case "lock" -> lock(player, subject);
                case "creator" -> {
                    ResourceLocation id = requiredId(subject);
                    boolean known = CreatorRuntime.catalog().isPresent()
                            && SkillRuntime.catalog().flatMap(value -> value.skill(id)).isPresent();
                    yield new Facts(known, known);
                }
                case "pvp_assist" -> {
                    java.util.UUID.fromString(subject);
                    boolean available = CombatContributionProfiles.active().isPresent()
                            && SocialProviderRuntime.parties(player.getServer()).available()
                            && PvpAwardLedgerSavedData.get(player.getServer()).active();
                    yield new Facts(true, available);
                }
                default -> new Facts(false, false);
            };
        } catch (RuntimeException exception) {
            return new Facts(false, false);
        }
    }

    private static Facts ability(ServerPlayer player, String subject) {
        ResourceLocation id = requiredId(subject);
        boolean known = AbilityRuntime.catalog().flatMap(value -> value.ability(id)).isPresent();
        if (!known) {
            return new Facts(false, false);
        }
        AbilityState state = AbilityRuntime.state(player);
        var slot = state.assignments().entrySet().stream()
                .filter(entry -> entry.getValue().equals(id)).map(Map.Entry::getKey).findFirst();
        boolean eligible = slot.isPresent() && AbilityRuntime.previewActivation(
                player, AbilityState.slotIndex(slot.orElseThrow())).allowed();
        return new Facts(true, eligible);
    }

    private static Facts lock(ServerPlayer player, String subject) {
        int separator = subject.indexOf(". ");
        if (separator < 1 || separator + 2 >= subject.length()) {
            return new Facts(false, false);
        }
        String operation = subject.substring(0, separator);
        ResourceLocation id = requiredId(subject.substring(separator + 2));
        return switch (operation) {
            case "class select" -> preview(ClassRuntime.previewSelect(player, id).blockers());
            case "class respec" -> preview(ClassRuntime.previewRespec(player, id).blockers());
            case "class swap" -> {
                boolean known = ClassRuntime.catalog().flatMap(value -> value.classDefinition(id)).isPresent();
                yield new Facts(known, known);
            }
            case "tree purchase" -> treeNode(player, id, true);
            case "tree refund" -> treeNode(player, id, false);
            case "tree respec" -> preview(TreeRuntime.previewRespec(player, id).blockers());
            default -> new Facts(false, false);
        };
    }

    private static Facts treeNode(ServerPlayer player, ResourceLocation node, boolean purchase) {
        var catalog = TreeRuntime.catalog().orElseThrow(
                () -> new IllegalStateException("Tree catalog is unavailable"));
        var tree = catalog.trees().values().stream()
                .filter(value -> value.nodesById().containsKey(node)).findFirst().orElse(null);
        if (tree == null) {
            return new Facts(false, false);
        }
        List<String> blockers = purchase
                ? TreeRuntime.previewPurchase(player, tree.id(), node).blockers()
                : TreeRuntime.previewRefund(player, tree.id(), node).blockers();
        return preview(blockers);
    }

    private static Facts preview(List<String> blockers) {
        return new Facts(true, blockers.isEmpty());
    }

    private static ResourceLocation requiredId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Replay subject is not a stable id");
        }
        return id;
    }

    static String statePrefix(String type, String subject) {
        return "replay.subject." + digest(type + "\u0000" + subject).substring(0, 24);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Facts(boolean known, boolean eligible) {
    }
}
