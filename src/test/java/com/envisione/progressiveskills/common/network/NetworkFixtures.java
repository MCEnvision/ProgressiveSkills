package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class NetworkFixtures {
    static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000601");
    static final UUID SERVER = UUID.fromString("00000000-0000-0000-0000-000000000602");
    static final String SEMANTIC = "1".repeat(64);
    static final ResourceLocation TREE = ResourceLocation.parse("example:combat");
    static final ResourceLocation NODE_ROOT = ResourceLocation.parse("example:root");
    static final ResourceLocation NODE_BRANCH = ResourceLocation.parse("example:branch");
    static final ResourceLocation CURRENCY = ResourceLocation.parse("example:points");
    static final ResourceLocation CLASS_SLOT = ResourceLocation.parse("example:combat_slot");
    static final ResourceLocation CLASS_MAGE = ResourceLocation.parse("example:mage");
    static final ResourceLocation CLASS_WARRIOR = ResourceLocation.parse("example:warrior");
    static final ResourceLocation CLASS_SYNERGY = ResourceLocation.parse("example:spellblade");
    static final ResourceLocation ABILITY_GUARD = ResourceLocation.parse("example:guard");
    static final ResourceLocation ABILITY_FOCUS = ResourceLocation.parse("example:focus");

    private NetworkFixtures() {
    }

    static DefinitionProjection definitions() {
        return new DefinitionProjection(Map.of());
    }

    static VisiblePlayerState state(long syncRevision, long stateRevision, Map<String, Long> balances) {
        return state(definitions(), syncRevision, stateRevision, balances, Map.of());
    }

    static VisiblePlayerState state(
            long syncRevision,
            long stateRevision,
            Map<String, Long> balances,
            Map<ResourceLocation, Integer> nodeRanks
    ) {
        return state(definitions(), syncRevision, stateRevision, balances, nodeRanks);
    }

    static VisiblePlayerState state(
            DefinitionProjection definitions,
            long syncRevision,
            long stateRevision,
            Map<String, Long> balances,
            Map<ResourceLocation, Integer> nodeRanks
    ) {
        return state(definitions, syncRevision, stateRevision, balances, nodeRanks, false);
    }

    static VisiblePlayerState state(
            DefinitionProjection definitions,
            long syncRevision,
            long stateRevision,
            Map<String, Long> balances,
            Map<ResourceLocation, Integer> nodeRanks,
            boolean quarantined
    ) {
        String presentation = BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(definitions));
        return new VisiblePlayerState(
                PLAYER, syncRevision, stateRevision, new DefinitionRevision(1, SEMANTIC),
                1, presentation, balances, Map.of(), nodeRanks, 0, 0, quarantined);
    }

    static DefinitionProjection treeDefinitions() {
        var text = new DefinitionProjection.Text(Optional.empty(), "Combat");
        var icon = new DefinitionProjection.Icon(
                "item", List.of(ResourceLocation.parse("minecraft:diamond")),
                ResourceLocation.parse("minecraft:barrier"), text, text
        );
        var root = new DefinitionProjection.NodeView(
                NODE_ROOT, new DefinitionProjection.Text(Optional.empty(), "Root"),
                Optional.empty(), icon, List.of(), 1, 0, 0,
                List.of(), List.of(), Map.of()
        );
        var branch = new DefinitionProjection.NodeView(
                NODE_BRANCH, new DefinitionProjection.Text(Optional.empty(), "Branch"),
                Optional.empty(), icon, List.of(), 2, 1, 0,
                List.of(NODE_ROOT), List.of(), Map.of()
        );
        var tree = new DefinitionProjection.TreeView(
                true, "global", Optional.empty(), CURRENCY, -5, 7, List.of(root, branch));
        return new DefinitionProjection(Map.of(
                new DefinitionKey(DefinitionKinds.TREE, TREE),
                new DefinitionProjection.Entry(
                        Optional.of(text), Optional.empty(), Optional.of(icon), List.of(), Optional.of(tree))
        ));
    }

    static DefinitionProjection classDefinitions() {
        var text = new DefinitionProjection.Text(Optional.empty(), "Class");
        var icon = new DefinitionProjection.Icon(
                "item", List.of(ResourceLocation.parse("minecraft:book")),
                ResourceLocation.parse("minecraft:barrier"), text, text
        );
        var slot = new DefinitionProjection.ClassSlotView(2, "confirmed");
        var classView = new DefinitionProjection.ClassView(
                true, CLASS_SLOT, 0, false,
                List.of(ResourceLocation.parse("example:caster")),
                Map.of(ResourceLocation.parse("example:arcana"), 5),
                List.of(NODE_ROOT), List.of(),
                Optional.of(new DefinitionProjection.CurrencyCostView(CURRENCY, 4)),
                true, Optional.of(new DefinitionProjection.CurrencyCostView(CURRENCY, 2)),
                List.of(new DefinitionProjection.StarterItemView(
                        ResourceLocation.parse("minecraft:book"), 1)),
                List.of(new DefinitionProjection.GrantSummary(
                        "ability", CLASS_SYNERGY, "owned", "highest", 1))
        );
        var synergy = new DefinitionProjection.SynergyView(
                true, Optional.of(text), Optional.empty(), Optional.of(icon), List.of("hybrid"),
                List.of(CLASS_MAGE, CLASS_WARRIOR),
                List.of(new DefinitionProjection.GrantSummary(
                        "tree_access", TREE, "owned", "highest", 1))
        );
        return new DefinitionProjection(Map.of(
                new DefinitionKey(DefinitionKinds.CLASS_SLOT, CLASS_SLOT),
                new DefinitionProjection.Entry(
                        Optional.of(text), Optional.empty(), Optional.of(icon), List.of(),
                        Optional.empty(), Optional.of(slot), Optional.empty()),
                new DefinitionKey(DefinitionKinds.CLASS, CLASS_MAGE),
                new DefinitionProjection.Entry(
                        Optional.of(text), Optional.empty(), Optional.of(icon), List.of(),
                        Optional.empty(), Optional.empty(), Optional.of(classView)),
                new DefinitionKey(DefinitionKinds.CLASS, CLASS_WARRIOR),
                new DefinitionProjection.Entry(
                        Optional.of(text), Optional.empty(), Optional.of(icon), List.of(),
                        Optional.empty(), Optional.empty(), Optional.of(classView))
        ), Map.of(CLASS_SYNERGY, synergy));
    }

    static DefinitionProjection abilityDefinitions() {
        var text = new DefinitionProjection.Text(Optional.empty(), "Guard");
        var icon = new DefinitionProjection.Icon(
                "item", List.of(ResourceLocation.parse("minecraft:shield")),
                ResourceLocation.parse("minecraft:barrier"), text, text
        );
        var active = new DefinitionProjection.AbilityView(
                true, "active", true, false, List.of(),
                List.of(
                        new DefinitionProjection.AbilityCostView(
                                ResourceLocation.parse("example:guard/points"), "currency",
                                Optional.of(CURRENCY), 2),
                        new DefinitionProjection.AbilityCostView(
                                ResourceLocation.parse("example:guard/hunger"), "hunger",
                                Optional.empty(), 1)
                ),
                new DefinitionProjection.AbilityTargetView("self", 0, false),
                ResourceLocation.parse("example:defense"), 20, 2, 40,
                List.of(
                        new DefinitionProjection.AbilityActionView(
                                ResourceLocation.parse("example:guard/heal"), "heal",
                                Optional.empty(), Optional.empty(), 2_000, 0,
                                false, false, false),
                        new DefinitionProjection.AbilityActionView(
                                ResourceLocation.parse("example:guard/effect"), "vanilla_effect",
                                Optional.empty(), Optional.of(
                                ResourceLocation.parse("minecraft:resistance")), 1, 100,
                                false, true, true)
                )
        );
        var toggle = new DefinitionProjection.AbilityView(
                true, "toggle", true, true,
                List.of(new DefinitionProjection.AbilityEffectView(
                        ResourceLocation.parse("example:focus/effect"), "flag",
                        ResourceLocation.parse("example:focused"), "owned", "highest", 1)),
                List.of(), new DefinitionProjection.AbilityTargetView("self", 0, false),
                ResourceLocation.parse("example:focus"), 0, 1, 0, List.of()
        );
        return new DefinitionProjection(Map.of(
                new DefinitionKey(DefinitionKinds.ABILITY, ABILITY_GUARD),
                new DefinitionProjection.Entry(
                        Optional.of(text), Optional.empty(), Optional.of(icon), List.of("defense"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(active)),
                new DefinitionKey(DefinitionKinds.ABILITY, ABILITY_FOCUS),
                new DefinitionProjection.Entry(
                        Optional.of(text), Optional.empty(), Optional.of(icon), List.of("stance"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(toggle))
        ));
    }
}
