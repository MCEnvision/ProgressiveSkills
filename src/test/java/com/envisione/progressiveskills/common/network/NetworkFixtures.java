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
}
