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
}
