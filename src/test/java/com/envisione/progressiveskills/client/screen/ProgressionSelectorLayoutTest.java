package com.envisione.progressiveskills.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionSelectorLayoutTest {
    @Test
    void compactScreenKeepsBothSelectorRegionsInsideTheWorkbench() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(320, 240);
        int skillTop = ProgressionScreen.skillGridTop(frame);
        int visibleSlots = ProgressionScreen.visibleClassSlots(frame);
        int lastSlotBottom = frame.contentY() + 6 + (visibleSlots - 1) * 29 + 26;

        assertTrue(skillTop >= frame.contentY() + 62);
        assertTrue(skillTop + 32 <= frame.contentBottom() - 2);
        assertTrue(ProgressionScreen.skillColumns(frame) >= 6);
        assertTrue(ProgressionScreen.classSlotWidth(frame) >= 68);
        assertTrue(ProgressionScreen.classColumns(frame) >= 1);
        assertTrue(lastSlotBottom < frame.contentBottom() - 22);
    }

    @Test
    void maximumScreenUsesTheExpandedSelectorCapacity() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(1920, 1080);

        assertTrue(ProgressionScreen.skillColumns(frame) >= 13);
        assertTrue(ProgressionScreen.classColumns(frame) >= 5);
        assertTrue(ProgressionScreen.visibleClassSlots(frame) >= 9);
    }
}
