package com.envisione.progressiveskills.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionWorkbenchLayoutTest {
    @Test
    void compactFrameKeepsEveryWorkbenchRegionSeparate() {
        var layout = ProgressionWorkbenchLayout.calculate(AdvancementUi.largeFrame(320, 240));

        assertTrue(layout.stacked());
        assertTrue(layout.dossier().right() < layout.selector().left());
        assertTrue(layout.selector().right() < layout.header().left());
        assertTrue(layout.header().bottom() < layout.graph().top());
        assertTrue(layout.graph().bottom() < layout.details().top());
        assertTrue(layout.player().top() > layout.balances().bottom());
        assertTrue(layout.selectorCapacity() >= 4);
    }

    @Test
    void maximumFrameUsesSideBySideGraphAndDetails() {
        var layout = ProgressionWorkbenchLayout.calculate(AdvancementUi.largeFrame(1920, 1080));

        assertFalse(layout.stacked());
        assertTrue(layout.graph().right() < layout.details().left());
        assertEquals(layout.graph().top(), layout.details().top());
        assertTrue(layout.player().height() >= 120);
        assertTrue(layout.selectorCapacity() >= 9);
    }

    @Test
    void fittedNodesStayInsideTheGraph() {
        var layout = ProgressionWorkbenchLayout.calculate(AdvancementUi.largeFrame(1920, 1080));
        var points = ProgressionWorkbenchLayout.fitNodes(List.of(
                new ProgressionWorkbenchLayout.NodeAnchor<>("major", -2, 0),
                new ProgressionWorkbenchLayout.NodeAnchor<>("left", 0, -2),
                new ProgressionWorkbenchLayout.NodeAnchor<>("right", 0, 2),
                new ProgressionWorkbenchLayout.NodeAnchor<>("choice", 2, 0)
        ), layout.graph());

        assertEquals(4, points.size());
        points.values().forEach(point -> assertTrue(
                layout.graph().contains(point.x(), point.y())
                        && point.x() >= layout.graph().left() + 14
                        && point.x() <= layout.graph().right() - 14
                        && point.y() >= layout.graph().top() + 14
                        && point.y() <= layout.graph().bottom() - 14
        ));
    }

    @Test
    void hoverTakesPrecedenceAndStalePinnedNodesFallBack() {
        List<String> nodes = List.of("major", "left", "right");

        assertEquals("right", ProgressionWorkbenchLayout.inspectedValue(
                Optional.of("right"), "left", "major", nodes));
        assertEquals("left", ProgressionWorkbenchLayout.inspectedValue(
                Optional.empty(), "left", "major", nodes));
        assertEquals("major", ProgressionWorkbenchLayout.inspectedValue(
                Optional.empty(), "removed", "major", nodes));
    }

    @Test
    void progressionNavigationContainsOnlyTheFourPlayerPages() {
        assertEquals(
                List.of(
                        ProgressionScreen.Tab.MAIN,
                        ProgressionScreen.Tab.SKILLS,
                        ProgressionScreen.Tab.CLASSES,
                        ProgressionScreen.Tab.ABILITIES
                ),
                ProgressionScreen.navigationTabs()
        );
    }

    @Test
    void treeZoomKeepsThePointerAnchoredAndHonorsBounds() {
        var original = new ProgressionWorkbenchLayout.Viewport(12.0D, -8.0D, 1.0D);
        double worldX = 48.0D / original.zoom() - original.panX();
        double worldY = -24.0D / original.zoom() - original.panY();

        var zoomed = ProgressionWorkbenchLayout.zoomAround(
                original,
                2.0D,
                148.0D,
                76.0D,
                100.0D,
                100.0D,
                0.65D,
                1.65D
        );

        assertEquals(48.0D, (worldX + zoomed.panX()) * zoomed.zoom(), 0.000001D);
        assertEquals(-24.0D, (worldY + zoomed.panY()) * zoomed.zoom(), 0.000001D);
        assertEquals(1.65D, ProgressionWorkbenchLayout.zoomAround(
                zoomed, 100.0D, 100.0D, 100.0D, 100.0D, 100.0D, 0.65D, 1.65D).zoom());
        assertEquals(0.65D, ProgressionWorkbenchLayout.zoomAround(
                zoomed, -100.0D, 100.0D, 100.0D, 100.0D, 100.0D, 0.65D, 1.65D).zoom());
    }
}
