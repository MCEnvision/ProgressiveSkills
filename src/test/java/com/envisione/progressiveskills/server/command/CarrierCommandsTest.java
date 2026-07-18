package com.envisione.progressiveskills.server.command;

import com.envisione.progressiveskills.common.carrier.CarrierCurrencyAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillLevelAction;
import com.envisione.progressiveskills.common.carrier.CarrierSkillXpAction;
import com.envisione.progressiveskills.common.carrier.CarrierTreeRespecAction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierCommandsTest {
    @Test
    void claimIdsMustUseCanonicalUuidText() {
        String value = "00000000-0000-0000-0000-000000001301";

        assertEquals(UUID.fromString(value), CarrierCommands.requireClaimId(value));
        assertThrows(IllegalArgumentException.class, () ->
                CarrierCommands.requireClaimId("0-0-0-0-1301"));
        assertThrows(IllegalArgumentException.class, () ->
                CarrierCommands.requireClaimId("00000000-0000-0000-0000-00000000130A"));
    }

    @Test
    void migrationDigestsMustUseBoundedLowercaseHexadecimal() {
        String digest = "a".repeat(64);

        assertEquals(digest, CarrierCommands.requireDigest(digest));
        assertThrows(IllegalArgumentException.class, () ->
                CarrierCommands.requireDigest("a".repeat(63)));
        assertThrows(IllegalArgumentException.class, () ->
                CarrierCommands.requireDigest("A" + "a".repeat(63)));
        assertThrows(IllegalArgumentException.class, () ->
                CarrierCommands.requireDigest("z".repeat(64)));
    }

    @Test
    void actionTextIncludesEveryCoreTargetAndChargeCost() {
        String xp = CarrierCommands.actionText(new CarrierSkillXpAction(
                id("xp"), id("physique"), 500, 1
        ));
        String level = CarrierCommands.actionText(new CarrierSkillLevelAction(
                id("level"), id("physique"), 2, 1
        ));
        String currency = CarrierCommands.actionText(new CarrierCurrencyAction(
                id("currency"), id("points"), 25, 0
        ));
        String tree = CarrierCommands.actionText(new CarrierTreeRespecAction(
                id("respec"), id("physique_training"), 1
        ));

        assertTrue(xp.contains("Fixed point amount 500"));
        assertTrue(level.contains("Levels 2"));
        assertTrue(currency.contains("Currency test:points"));
        assertTrue(tree.contains("Tree test:physique_training"));
        assertTrue(tree.endsWith("Consumes 1."));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
