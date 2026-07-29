package com.envisione.progressiveskills.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionSelectorLayoutTest {
    @Test
    void compactScreenKeepsBothSelectorRegionsInsideTheWorkbench() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(320, 240);
        ProgressionWorkbenchLayout.Layout workbench = ProgressionWorkbenchLayout.calculate(frame);
        int visibleSlots = ProgressionScreen.visibleClassSlots(frame);
        int lastSlotBottom = frame.contentY() + 6 + (visibleSlots - 1) * 29 + 26;

        assertTrue(workbench.selectorCapacity() >= 4);
        assertTrue(workbench.dossier().right() < workbench.selector().left());
        assertTrue(workbench.selector().right() < workbench.header().left());
        assertTrue(ProgressionScreen.classSlotWidth(frame) >= 68);
        assertTrue(ProgressionScreen.classColumns(frame) >= 1);
        assertTrue(lastSlotBottom < frame.contentBottom() - 22);
    }

    @Test
    void maximumScreenUsesTheExpandedSelectorCapacity() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(1920, 1080);

        assertTrue(ProgressionWorkbenchLayout.calculate(frame).selectorCapacity() >= 9);
        assertTrue(ProgressionScreen.classColumns(frame) >= 5);
        assertTrue(ProgressionScreen.visibleClassSlots(frame) >= 9);
    }
}
