package com.envisione.progressiveskills.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTextTest {
    @Test
    void legacyFormattingCombinesColorAndBoldStyle() {
        Component component = UiText.legacy("&l&cMomentum");

        assertEquals("Momentum", component.getString());
        var style = component.getSiblings().getFirst().getStyle();
        assertTrue(style.isBold());
        assertEquals(ChatFormatting.RED.getColor(), style.getColor().getValue());
    }

    @Test
    void legacyFormattingSupportsLiteralAmpersandAndReset() {
        Component component = UiText.legacy("&aReady && waiting&r plain");

        assertEquals("Ready & waiting plain", component.getString());
        assertFalse(component.getSiblings().getLast().getStyle().isBold());
    }

    @Test
    void stableIdsBecomeReadableLabels() {
        assertEquals("Global Points", UiText.prettyId(
                ResourceLocation.parse("progressiveskills:global_points")));
        assertEquals("Warrior Cleave Rank 2", UiText.prettyPath("warrior/cleave.rank_2"));
    }
}
