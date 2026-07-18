package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.common.network.ClientNetworkState;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TreeScreen extends ProgressiveScreen {
    private static final int GRID_TOP = 62;
    private static final int NODE_WIDTH = 138;
    private static final int NODE_HEIGHT = 20;
    private static final int GRID_GAP = 6;

    private List<TreeModel> trees = List.of();
    private int treeIndex;
    private int page;
    private ResourceLocation selectedNode;
    private final Map<ResourceLocation, Point> nodeCenters = new LinkedHashMap<>();
    private String snapshotFingerprint = "";

    public TreeScreen() {
        super(Component.translatable("screen.progressiveskills.tree.title"));
    }

    public static boolean isAvailable(ClientNetworkState.Snapshot snapshot) {
        return snapshot.phase() == ClientNetworkState.ClientPhase.ACTIVE
                && snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().values().stream())
                .anyMatch(entry -> entry.tree().isPresent());
    }

    @Override
    protected void init() {
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        refreshModels(snapshot);
        nodeCenters.clear();
        if (trees.isEmpty()) {
            addRenderableWidget(Button.builder(
                    Component.translatable("gui.done"), button -> onClose()
            ).bounds(Math.max(8, width / 2 - 50), Math.max(8, height - 28), 100, 20).build());
            snapshotFingerprint = fingerprint(snapshot);
            return;
        }

        int topButtonWidth = Math.max(54, Math.min(72, (width - 40) / 4));
        int topGap = 4;
        int topTotal = topButtonWidth * 4 + topGap * 3;
        int topX = Math.max(8, (width - topTotal) / 2);
        Button previousTree = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.previous_tree"),
                button -> changeTree(-1)
        ).bounds(topX, 34, topButtonWidth, 20).build());
        Button previousPage = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.previous_page"),
                button -> changePage(-1)
        ).bounds(topX + topButtonWidth + topGap, 34, topButtonWidth, 20).build());
        Button nextPage = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.next_page"),
                button -> changePage(1)
        ).bounds(topX + (topButtonWidth + topGap) * 2, 34, topButtonWidth, 20).build());
        Button nextTree = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.next_tree"),
                button -> changeTree(1)
        ).bounds(topX + (topButtonWidth + topGap) * 3, 34, topButtonWidth, 20).build());
        previousTree.active = trees.size() > 1;
        nextTree.active = trees.size() > 1;

        int columns = gridColumns();
        int rows = gridRows();
        int pageSize = columns * rows;
        TreeModel tree = currentTree();
        int pageCount = Math.max(1, (tree.nodes().size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        previousPage.active = page > 0;
        nextPage.active = page + 1 < pageCount;
        int first = page * pageSize;
        int last = Math.min(tree.nodes().size(), first + pageSize);
        int gridWidth = columns * NODE_WIDTH + Math.max(0, columns - 1) * GRID_GAP;
        int gridX = Math.max(8, (width - gridWidth) / 2);
        Button selectedButton = null;
        VisiblePlayerState state = snapshot.visibleState().orElseThrow();
        for (int index = first; index < last; index++) {
            DefinitionProjection.NodeView node = tree.nodes().get(index);
            int local = index - first;
            int x = gridX + local % columns * (NODE_WIDTH + GRID_GAP);
            int y = GRID_TOP + local / columns * (NODE_HEIGHT + GRID_GAP);
            NodeStatus status = status(tree, node, state);
            Component label = nodeLabel(node, status);
            Component narration = nodeNarration(tree, node, status);
            Button nodeButton = addRenderableWidget(Button.builder(label, button -> {
                selectedNode = node.id();
                rebuildWidgets();
            }).tooltip(Tooltip.create(narration)).bounds(x, y, NODE_WIDTH, NODE_HEIGHT).build());
            nodeCenters.put(node.id(), new Point(x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2));
            if (node.id().equals(selectedNode)) {
                selectedButton = nodeButton;
            }
        }

        DefinitionProjection.NodeView selected = selectedNode();
        NodeStatus selectedStatus = status(tree, selected, state);
        Component selectedNarration = nodeNarration(tree, selected, selectedStatus);
        int actionY = Math.max(GRID_TOP + rows * (NODE_HEIGHT + GRID_GAP) + 34, height - 26);
        int gap = 4;
        int actionWidth = Math.max(54, (Math.max(240, width) - 32 - gap * 3) / 4);
        int actionTotal = actionWidth * 4 + gap * 3;
        int actionX = Math.max(8, (width - actionTotal) / 2);
        Button buy = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.buy"),
                button -> PsNetworking.sendTreeBuy(tree.id(), selected.id())
        ).tooltip(Tooltip.create(actionNarration(
                Component.translatable("screen.progressiveskills.tree.buy"), selectedNarration
        ))).bounds(actionX, actionY, actionWidth, 20).build());
        Button preview = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.refund_preview"),
                button -> PsNetworking.sendTreeRefundPreview(tree.id(), selected.id())
        ).tooltip(Tooltip.create(actionNarration(
                Component.translatable("screen.progressiveskills.tree.refund_preview"), selectedNarration
        ))).bounds(actionX + actionWidth + gap, actionY, actionWidth, 20).build());
        Optional<NetworkPayloads.TreeRefundPreview> currentPreview = matchingPreview(snapshot);
        Component confirmReview = currentPreview.map(TreeScreen::previewReview).orElseGet(() ->
                Component.translatable("screen.progressiveskills.tree.preview_unavailable"));
        Button confirm = addRenderableWidget(Button.builder(
                Component.translatable("screen.progressiveskills.tree.refund_confirm"),
                button -> matchingPreview(PsNetworking.clientSnapshot()).ifPresent(value ->
                        PsNetworking.sendTreeRefundConfirm(
                                tree.id(), selected.id(), value.previewDigest()))
        ).tooltip(Tooltip.create(confirmReview))
                .bounds(actionX + (actionWidth + gap) * 2, actionY, actionWidth, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"), button -> onClose()
        ).bounds(actionX + (actionWidth + gap) * 3, actionY, actionWidth, 20).build());
        buy.active = selectedStatus == NodeStatus.AVAILABLE;
        preview.active = selectedStatus == NodeStatus.OWNED;
        confirm.active = currentPreview.filter(NetworkPayloads.TreeRefundPreview::allowed).isPresent();
        if (selectedButton != null) {
            setInitialFocus(selectedButton);
        }
        snapshotFingerprint = fingerprint(snapshot);
    }

    @Override
    public void tick() {
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        String current = fingerprint(snapshot);
        if (!current.equals(snapshotFingerprint)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        graphics.fill(6, 6, width - 6, height - 6, 0xB8101010);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (!trees.isEmpty()) {
            TreeModel tree = currentTree();
            graphics.drawCenteredString(font, tree.display(), width / 2, 23, 0xE0E0E0);
            renderConnectors(graphics, tree);
            renderDetails(graphics, tree, PsNetworking.clientSnapshot());
        } else {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.progressiveskills.tree.unavailable"),
                    width / 2, height / 2 - 5, 0xAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        var narration = Component.empty().append(title);
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        if (trees.isEmpty() || selectedNode == null || snapshot.visibleState().isEmpty()) {
            return narration.append(". ").append(
                    Component.translatable("screen.progressiveskills.tree.unavailable"));
        }
        TreeModel tree = currentTree();
        DefinitionProjection.NodeView node = selectedNode();
        narration.append(". ").append(tree.display()).append(". ").append(tree.id().toString());
        narration.append(". ").append(nodeNarration(
                tree, node, status(tree, node, snapshot.visibleState().orElseThrow())
        ));
        snapshot.lastIntentResult().ifPresent(result -> narration.append(". ").append(
                Component.translatable(
                        "screen.progressiveskills.tree.latest_result",
                        result.status().name(),
                        result.message()
                )
        ));
        return narration;
    }

    private void renderConnectors(GuiGraphics graphics, TreeModel tree) {
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            Point target = nodeCenters.get(node.id());
            if (target == null) {
                continue;
            }
            for (ResourceLocation required : node.requires()) {
                renderConnector(graphics, nodeCenters.get(required), target, 0xFF707070);
            }
            for (ResourceLocation required : node.requiresAny()) {
                renderConnector(graphics, nodeCenters.get(required), target, 0xFF506A8A);
            }
        }
    }

    private static void renderConnector(GuiGraphics graphics, Point source, Point target, int color) {
        if (source == null) {
            return;
        }
        int middle = (source.y() + target.y()) / 2;
        graphics.vLine(source.x(), Math.min(source.y(), middle), Math.max(source.y(), middle), color);
        graphics.hLine(Math.min(source.x(), target.x()), Math.max(source.x(), target.x()), middle, color);
        graphics.vLine(target.x(), Math.min(middle, target.y()), Math.max(middle, target.y()), color);
    }

    private void renderDetails(
            GuiGraphics graphics,
            TreeModel tree,
            ClientNetworkState.Snapshot snapshot
    ) {
        if (snapshot.visibleState().isEmpty()) {
            return;
        }
        DefinitionProjection.NodeView node = selectedNode();
        NodeStatus status = status(tree, node, snapshot.visibleState().orElseThrow());
        int detailsY = GRID_TOP + gridRows() * (NODE_HEIGHT + GRID_GAP) + 2;
        int bottom = Math.max(detailsY, height - 30);
        int line = detailsY;
        graphics.drawString(font, nodeLabel(node, status), 14, line, status.color(), false);
        line += font.lineHeight + 1;
        if (snapshot.lastIntentResult().isPresent() && line < bottom) {
            NetworkPayloads.IntentResult result = snapshot.lastIntentResult().orElseThrow();
            graphics.drawString(font, Component.translatable(
                    "screen.progressiveskills.tree.latest_result", result.status().name(), result.message()),
                    14, line, result.status() == NetworkPayloads.IntentStatus.ACCEPTED
                            ? 0x7CFC98 : 0xFF8888, false);
            line += font.lineHeight + 1;
        }
        graphics.drawString(font, Component.translatable(
                "screen.progressiveskills.tree.cost", node.cost(), tree.tree().currency()),
                14, line, 0xC8C8C8, false);
        line += font.lineHeight + 1;
        Optional<NetworkPayloads.TreeRefundPreview> preview = matchingPreview(snapshot);
        if (preview.isPresent() && line < bottom) {
            NetworkPayloads.TreeRefundPreview value = preview.orElseThrow();
            Component summary = value.allowed()
                    ? Component.translatable("screen.progressiveskills.tree.preview_ready",
                    value.affectedNodes().size(), refundSummary(value.refundBalances()))
                    : Component.literal(value.blockers().getFirst());
            graphics.drawString(font, summary, 14, line,
                    value.allowed() ? 0x7CFC98 : 0xFF7777, false);
            line += font.lineHeight + 1;
        }
        if (node.description().isPresent() && line < bottom) {
            for (var text : font.split(component(node.description().orElseThrow()), width - 28)) {
                if (line + font.lineHeight > bottom) {
                    break;
                }
                graphics.drawString(font, text, 14, line, 0xA8A8A8, false);
                line += font.lineHeight;
            }
        }
    }

    private void refreshModels(ClientNetworkState.Snapshot snapshot) {
        ResourceLocation previousTree = trees.isEmpty() ? null : currentTree().id();
        var next = new ArrayList<TreeModel>();
        snapshot.activeDefinitions().ifPresent(projection -> projection.definitions().forEach((key, entry) ->
                entry.tree().ifPresent(tree -> next.add(new TreeModel(
                        key.id(), entryDisplay(key.id(), entry), tree, orderedNodes(tree.nodes())
                )))
        ));
        next.sort(Comparator.comparing(TreeModel::id, ResourceLocation::compareNamespaced));
        trees = List.copyOf(next);
        if (trees.isEmpty()) {
            treeIndex = 0;
            selectedNode = null;
            page = 0;
            return;
        }
        treeIndex = previousTree == null ? 0 : Math.max(0, indexOfTree(previousTree));
        TreeModel tree = currentTree();
        if (selectedNode == null || tree.nodes().stream().noneMatch(node -> node.id().equals(selectedNode))) {
            selectedNode = tree.nodes().getFirst().id();
            page = 0;
        }
    }

    private void changeTree(int direction) {
        treeIndex = Math.floorMod(treeIndex + direction, trees.size());
        selectedNode = currentTree().nodes().getFirst().id();
        page = 0;
        rebuildWidgets();
    }

    private void changePage(int direction) {
        page += direction;
        int first = page * gridColumns() * gridRows();
        if (first >= 0 && first < currentTree().nodes().size()) {
            selectedNode = currentTree().nodes().get(first).id();
        }
        rebuildWidgets();
    }

    private Optional<NetworkPayloads.TreeRefundPreview> matchingPreview(
            ClientNetworkState.Snapshot snapshot
    ) {
        if (trees.isEmpty() || selectedNode == null || snapshot.visibleState().isEmpty()) {
            return Optional.empty();
        }
        return snapshot.treeRefundPreview().filter(preview ->
                preview.treeId().equals(currentTree().id())
                        && preview.nodeId().equals(selectedNode)
                        && preview.stateRevision() == snapshot.visibleState().orElseThrow().stateRevision());
    }

    private DefinitionProjection.NodeView selectedNode() {
        return currentTree().nodes().stream().filter(node -> node.id().equals(selectedNode))
                .findFirst().orElse(currentTree().nodes().getFirst());
    }

    private TreeModel currentTree() {
        return trees.get(treeIndex);
    }

    private int indexOfTree(ResourceLocation id) {
        for (int index = 0; index < trees.size(); index++) {
            if (trees.get(index).id().equals(id)) {
                return index;
            }
        }
        return 0;
    }

    private int gridColumns() {
        return Math.max(1, Math.min(4, (Math.max(width, 160) - 20) / (NODE_WIDTH + GRID_GAP)));
    }

    private int gridRows() {
        return Math.max(1, Math.min(5, Math.max(1, height - 142) / (NODE_HEIGHT + GRID_GAP)));
    }

    private static NodeStatus status(
            TreeModel tree,
            DefinitionProjection.NodeView node,
            VisiblePlayerState state
    ) {
        if (state.nodeRanks().containsKey(node.id())) {
            return NodeStatus.OWNED;
        }
        boolean requires = node.requires().stream().allMatch(state.nodeRanks()::containsKey);
        boolean requiresAny = node.requiresAny().isEmpty()
                || node.requiresAny().stream().anyMatch(state.nodeRanks()::containsKey);
        boolean levels = node.minimumSkillLevels().entrySet().stream().allMatch(entry ->
                state.balances().getOrDefault(SkillStateIds.level(entry.getKey()).toString(), 0L)
                        >= entry.getValue());
        boolean currency = currencyAvailable(tree, node, state);
        return tree.tree().enabled() && !state.quarantined() && requires && requiresAny && levels && currency
                ? NodeStatus.AVAILABLE : NodeStatus.LOCKED;
    }

    private static Component nodeLabel(DefinitionProjection.NodeView node, NodeStatus status) {
        return Component.empty().append(component(node.display())).append(". ").append(status.label());
    }

    private static Component nodeNarration(
            TreeModel tree,
            DefinitionProjection.NodeView node,
            NodeStatus status
    ) {
        var narration = Component.empty().append(nodeLabel(node, status)).append(". ").append(
                Component.translatable("screen.progressiveskills.tree.cost", node.cost(), tree.tree().currency()))
                .append(". ").append(Component.translatable(
                        "screen.progressiveskills.tree.currency_minimum", tree.tree().currencyMinimum()));
        if (!node.requires().isEmpty()) {
            narration.append(". ").append(Component.translatable(
                    "screen.progressiveskills.tree.requires", joinIds(node.requires())));
        }
        if (!node.requiresAny().isEmpty()) {
            narration.append(". ").append(Component.translatable(
                    "screen.progressiveskills.tree.requires_any", joinIds(node.requiresAny())));
        }
        if (!node.minimumSkillLevels().isEmpty()) {
            narration.append(". ").append(Component.translatable(
                    "screen.progressiveskills.tree.minimum_levels",
                    String.join(", ", node.minimumSkillLevels().entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue()).toList())));
        }
        return narration;
    }

    private static Component actionNarration(Component action, Component nodeNarration) {
        return Component.empty().append(action).append(". ").append(nodeNarration);
    }

    private static Component component(DefinitionProjection.Text text) {
        return text.localizationKey().map(key -> Component.translatableWithFallback(key, text.fallback()))
                .orElseGet(() -> Component.literal(text.fallback()));
    }

    private static Component entryDisplay(
            ResourceLocation id,
            DefinitionProjection.Entry entry
    ) {
        return entry.display().map(TreeScreen::component).orElseGet(() -> Component.literal(id.toString()));
    }

    private static List<DefinitionProjection.NodeView> orderedNodes(
            List<DefinitionProjection.NodeView> nodes
    ) {
        return nodes.stream().sorted(Comparator.comparingInt(DefinitionProjection.NodeView::row)
                .thenComparingInt(DefinitionProjection.NodeView::column)
                .thenComparing(DefinitionProjection.NodeView::id, ResourceLocation::compareNamespaced)).toList();
    }

    private static String refundSummary(Map<ResourceLocation, Long> balances) {
        if (balances.isEmpty()) {
            return "0";
        }
        return String.join(", ", balances.entrySet().stream()
                .map(entry -> entry.getValue() + " " + entry.getKey()).toList());
    }

    private static boolean currencyAvailable(
            TreeModel tree,
            DefinitionProjection.NodeView node,
            VisiblePlayerState state
    ) {
        long balance = tree.tree().visibleCurrencyBalance(state.balances());
        try {
            return Math.subtractExact(balance, node.cost()) >= tree.tree().currencyMinimum();
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static String joinIds(List<ResourceLocation> ids) {
        return String.join(", ", ids.stream().map(ResourceLocation::toString).toList());
    }

    private static Component previewReview(NetworkPayloads.TreeRefundPreview preview) {
        String affected = preview.affectedNodes().isEmpty()
                ? "None" : joinIds(preview.affectedNodes());
        String balances = refundSummary(preview.refundBalances());
        var review = Component.translatable(
                "screen.progressiveskills.tree.confirm_review", affected, balances);
        if (!preview.blockers().isEmpty()) {
            review.append(". ").append(String.join(", ", preview.blockers()));
        }
        return review;
    }

    private static String fingerprint(ClientNetworkState.Snapshot snapshot) {
        String state = snapshot.visibleState().map(value ->
                value.syncRevision() + ":" + value.stateRevision() + ":" + value.nodeRanks().hashCode())
                .orElse("none");
        String preview = snapshot.treeRefundPreview().map(value ->
                value.requestId() + ":" + value.previewDigest() + ":" + value.blockers().hashCode())
                .orElse("none");
        String result = snapshot.lastIntentResult().map(value ->
                value.resultId() + ":" + value.status()).orElse("none");
        return snapshot.phase() + ":" + snapshot.activeDefinitions().hashCode()
                + ":" + state + ":" + preview + ":" + result;
    }

    private enum NodeStatus {
        OWNED("screen.progressiveskills.tree.status.owned", 0x7CFC98),
        AVAILABLE("screen.progressiveskills.tree.status.available", 0x80C8FF),
        LOCKED("screen.progressiveskills.tree.status.locked", 0xFF8888);

        private final String key;
        private final int color;

        NodeStatus(String key, int color) {
            this.key = key;
            this.color = color;
        }

        Component label() {
            return Component.translatable(key);
        }

        int color() {
            return color;
        }
    }

    private record TreeModel(
            ResourceLocation id,
            Component display,
            DefinitionProjection.TreeView tree,
            List<DefinitionProjection.NodeView> nodes
    ) {
    }

    private record Point(int x, int y) {
    }
}
