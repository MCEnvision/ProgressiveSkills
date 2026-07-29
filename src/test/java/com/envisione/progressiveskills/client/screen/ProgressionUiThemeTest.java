package com.envisione.progressiveskills.client.screen;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionUiThemeTest {
    @Test
    void rgbColorsReceiveAnOpaqueAlphaChannel() {
        assertEquals(0xFF80C8FF,
                ProgressionUiTheme.parseColor(new JsonPrimitive("#80c8ff"), 0));
    }

    @Test
    void argbColorsPreserveTheirAlphaChannel() {
        assertEquals(0x7080C8FF,
                ProgressionUiTheme.parseColor(new JsonPrimitive("#7080c8ff"), 0));
    }

    @Test
    void invalidColorsUseTheirLocalFallback() {
        assertEquals(0xFF123456,
                ProgressionUiTheme.parseColor(new JsonPrimitive("blue"), 0xFF123456));
    }
}
