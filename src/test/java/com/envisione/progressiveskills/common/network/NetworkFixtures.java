package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;

import java.util.Map;
import java.util.UUID;

final class NetworkFixtures {
    static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000601");
    static final UUID SERVER = UUID.fromString("00000000-0000-0000-0000-000000000602");
    static final String SEMANTIC = "1".repeat(64);

    private NetworkFixtures() {
    }

    static DefinitionProjection definitions() {
        return new DefinitionProjection(Map.of());
    }

    static VisiblePlayerState state(long syncRevision, long stateRevision, Map<String, Long> balances) {
        var definitions = definitions();
        String presentation = BoundedNetworkCodec.digest(DefinitionProjectionCodec.encode(definitions));
        return new VisiblePlayerState(
                PLAYER, syncRevision, stateRevision, new DefinitionRevision(1, SEMANTIC),
                1, presentation, balances, Map.of(), 0, 0, false);
    }
}
