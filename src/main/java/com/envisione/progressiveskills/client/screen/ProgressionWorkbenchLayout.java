package com.envisione.progressiveskills.client.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ProgressionWorkbenchLayout {
    private static final int GAP = 3;
    private static final int SELECTOR_WIDTH = 30;
    private static final int SELECTOR_SIZE = 26;
    private static final int SELECTOR_GAP = 3;

    private ProgressionWorkbenchLayout() {
    }

    static Layout calculate(AdvancementUi.Frame frame) {
        Rect content = new Rect(
                frame.contentX() + 2,
                frame.contentY() + 2,
                frame.contentRight() - 2,
                frame.contentBottom() - 2
        );
        int dossierWidth = Math.clamp(content.width() * 27 / 100, 68, 128);
        Rect dossier = new Rect(content.left(), content.top(), content.left() + dossierWidth, content.bottom());
        Rect selector = new Rect(
                dossier.right() + GAP,
                content.top(),
                dossier.right() + GAP + SELECTOR_WIDTH,
                content.bottom()
        );
        Rect workspace = new Rect(selector.right() + GAP, content.top(), content.right(), content.bottom());
        int headerHeight = Math.min(28, Math.max(22, workspace.height() / 5));
        Rect header = new Rect(workspace.left(), workspace.top(), workspace.right(), workspace.top() + headerHeight);
        int bodyTop = header.bottom() + GAP;
        boolean stacked = workspace.width() < 250;
        Rect graph;
        Rect details;
        if (stacked) {
            int bodyHeight = Math.max(2, workspace.bottom() - bodyTop);
            int detailHeight = Math.clamp(bodyHeight * 42 / 100, 46, 82);
            int split = Math.max(bodyTop + 1, workspace.bottom() - detailHeight);
            graph = new Rect(workspace.left(), bodyTop, workspace.right(), Math.max(bodyTop + 1, split - GAP));
            details = new Rect(workspace.left(), split, workspace.right(), workspace.bottom());
        } else {
            int detailWidth = Math.clamp(workspace.width() / 3, 96, 124);
            int split = workspace.right() - detailWidth;
            graph = new Rect(workspace.left(), bodyTop, split - GAP, workspace.bottom());
            details = new Rect(split, bodyTop, workspace.right(), workspace.bottom());
        }
        int meterHeight = Math.min(42, Math.max(34, dossier.height() / 5));
        Rect meter = new Rect(
                dossier.left() + 4,
                dossier.top() + 4,
                dossier.right() - 4,
                dossier.top() + 4 + meterHeight
        );
        int remaining = Math.max(2, dossier.bottom() - meter.bottom() - 10);
        int balancesHeight = Math.clamp(remaining / 3, 34, 58);
        Rect balances = new Rect(
                dossier.left() + 4,
                meter.bottom() + GAP,
                dossier.right() - 4,
                meter.bottom() + GAP + balancesHeight
        );
        Rect player = new Rect(
                dossier.left() + 4,
                balances.bottom() + GAP,
                dossier.right() - 4,
                dossier.bottom() - 4
        );
        int selectorCapacity = Math.max(1, (selector.height() - 8 + SELECTOR_GAP)
                / (SELECTOR_SIZE + SELECTOR_GAP));
        return new Layout(
                content, dossier, meter, balances, player, selector, header, graph, details,
                stacked, selectorCapacity
        );
    }

    static <T> Map<T, Point> fitNodes(List<NodeAnchor<T>> nodes, Rect area) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(area, "area");
        if (nodes.isEmpty()) {
            return Map.of();
        }
        int minimumRow = nodes.stream().mapToInt(NodeAnchor::row).min().orElse(0);
        int maximumRow = nodes.stream().mapToInt(NodeAnchor::row).max().orElse(0);
        int minimumColumn = nodes.stream().mapToInt(NodeAnchor::column).min().orElse(0);
        int maximumColumn = nodes.stream().mapToInt(NodeAnchor::column).max().orElse(0);
        int rowSpan = maximumRow - minimumRow;
        int columnSpan = maximumColumn - minimumColumn;
        double rowStep = rowSpan == 0 ? 0.0D
                : Math.min(48.0D, Math.max(1, area.height() - 30) / (double) rowSpan);
        double columnStep = columnSpan == 0 ? 0.0D
                : Math.min(58.0D, Math.max(1, area.width() - 30) / (double) columnSpan);
        double rowCenter = (minimumRow + maximumRow) / 2.0D;
        double columnCenter = (minimumColumn + maximumColumn) / 2.0D;
        int margin = Math.min(18, Math.max(1, Math.min(area.width(), area.height()) / 2 - 1));
        var result = new LinkedHashMap<T, Point>();
        for (NodeAnchor<T> node : nodes) {
            int x = area.centerX() + (int) Math.round((node.column() - columnCenter) * columnStep);
            int y = area.centerY() + (int) Math.round((node.row() - rowCenter) * rowStep);
            result.put(node.key(), new Point(
                    Math.clamp(x, area.left() + margin, area.right() - margin),
                    Math.clamp(y, area.top() + margin, area.bottom() - margin)
            ));
        }
        return Map.copyOf(result);
    }

    static double activeShare(long activeXp, long bankedXp) {
        if (activeXp <= 0) {
            return 0.0D;
        }
        if (bankedXp <= 0) {
            return 1.0D;
        }
        return activeXp / ((double) activeXp + bankedXp);
    }

    static <T> T inspectedValue(
            Optional<T> hovered,
            T pinned,
            T fallback,
            List<T> available
    ) {
        Objects.requireNonNull(hovered, "hovered");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(available, "available");
        if (hovered.isPresent() && available.contains(hovered.orElseThrow())) {
            return hovered.orElseThrow();
        }
        return pinned != null && available.contains(pinned) ? pinned : fallback;
    }

    record Layout(
            Rect content,
            Rect dossier,
            Rect meter,
            Rect balances,
            Rect player,
            Rect selector,
            Rect header,
            Rect graph,
            Rect details,
            boolean stacked,
            int selectorCapacity
    ) {
    }

    record Rect(int left, int top, int right, int bottom) {
        Rect {
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("Workbench rectangle must have positive area");
            }
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        int centerX() {
            return left + width() / 2;
        }

        int centerY() {
            return top + height() / 2;
        }

        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    record NodeAnchor<T>(T key, int row, int column) {
        NodeAnchor {
            Objects.requireNonNull(key, "key");
        }
    }

    record Point(int x, int y) {
    }
}
