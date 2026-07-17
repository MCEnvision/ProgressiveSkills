package com.envisione.progressiveskills.server.network;

import com.envisione.progressiveskills.common.rule.RuleMemoryKeys;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkRuntimeTest {
    @Test
    void internalRuleMemoryNeverEntersVisiblePlayerBalances() {
        ResourceLocation rule = ResourceLocation.parse("test:block_rule");
        ResourceLocation internal = RuleMemoryKeys.forRule(rule).firstTime();
        ResourceLocation visible = ResourceLocation.parse("progressiveskills:skill_xp/test/value");

        Map<String, Long> balances = NetworkRuntime.visibleBalances(Map.of(
                internal, 1L,
                visible, 20L
        ));

        assertEquals(20L, balances.get(visible.toString()));
        assertFalse(balances.containsKey(internal.toString()));
    }
}
