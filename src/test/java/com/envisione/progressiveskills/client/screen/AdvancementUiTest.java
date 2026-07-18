package com.envisione.progressiveskills.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementUiTest {
    @Test
    void largeFrameKeepsTabsAndFooterInsideACompactScreen() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(320, 240);

        assertTrue(frame.x() >= 28);
        assertTrue(frame.y() >= 28);
        assertTrue(frame.right() <= 292);
        assertTrue(frame.footerY() + 20 <= 240);
        assertTrue(frame.width() > AdvancementUi.WINDOW_WIDTH);
        assertTrue(frame.height() > AdvancementUi.WINDOW_HEIGHT);
    }

    @Test
    void largeFrameStopsGrowingAtTheWorkspaceCap() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(1920, 1080);

        assertEquals(520, frame.width());
        assertEquals(330, frame.height());
        assertEquals(frame.width() - 18, frame.contentWidth());
        assertEquals(frame.height() - 27, frame.contentHeight());
    }
}
