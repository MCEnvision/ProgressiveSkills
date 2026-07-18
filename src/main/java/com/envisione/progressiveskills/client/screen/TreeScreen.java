package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ProjectionPresentation;
import com.envisione.progressiveskills.client.SafeRetryTray;
import com.envisione.progressiveskills.common.network.ClientNetworkState;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.lwjgl.glfw.GLFW;

public final class TreeScreen extends ProgressiveScreen {
    private static final int COLUMN_SPACING = 58;
    private static final int ROW_SPACING = 48;

    private List<TreeModel> trees = List.of();
    private int treeIndex;
    private ResourceLocation selectedNode;
    private double panX;
    private double panY;
    private final Map<ResourceLocation, Pan> rememberedPans = new LinkedHashMap<>();
    private boolean centerRequested = true;
    private boolean dragging;
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
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        if (trees.isEmpty() || snapshot.visibleState().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(frame.x() + 76, frame.footerY(), 100, 20).build());
            snapshotFingerprint = fingerprint(snapshot);
            return;
        }
        addTreeTabs(frame);
        TreeModel tree = currentTree();
        DefinitionProjection.NodeView node = selectedNode();
        VisiblePlayerState state = snapshot.visibleState().orElseThrow();
        NodeStatus selectedStatus = status(tree, node, state);
        Optional<NetworkPayloads.TreeRefundPreview> preview = matchingPreview(snapshot);
        int buttonWidth = 61;
        int gap = 2;
        int x = frame.x();
        int y = frame.footerY();
        int action = 0;
        if (selectedStatus == NodeStatus.AVAILABLE) {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.progressiveskills.tree.buy"), ignored -> send(
                            "Buy tree node " + node.id(), () -> PsNetworking.sendTreeBuy(tree.id(), node.id())))
                    .bounds(x + action++ * (buttonWidth + gap), y, buttonWidth, 20).build());
        }
        if (selectedStatus == NodeStatus.OWNED) {
            addRenderableWidget(Button.builder(
                    Component.literal("Refund"), ignored -> send(
                            "Preview tree refund " + node.id(),
                            () -> PsNetworking.sendTreeRefundPreview(tree.id(), node.id())))
                    .bounds(x + action++ * (buttonWidth + gap), y, buttonWidth, 20).build());
        }
        if (preview.filter(NetworkPayloads.TreeRefundPreview::allowed).isPresent()) {
            addRenderableWidget(Button.builder(
                    Component.literal("Confirm"), ignored -> matchingPreview(PsNetworking.clientSnapshot())
                            .ifPresent(value -> send(
                                    "Confirm tree refund " + node.id(),
                                    () -> PsNetworking.sendTreeRefundConfirm(
                                            tree.id(), node.id(), value.previewDigest()))))
                    .bounds(x + action * (buttonWidth + gap), y, buttonWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(frame.x() + 189, y, 63, 20).build());
        if (centerRequested) {
            centerTree(frame, tree);
            centerRequested = false;
        }
        snapshotFingerprint = fingerprint(snapshot);
    }

    @Override
    public void tick() {
        super.tick();
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        if (!fingerprint(snapshot).equals(snapshotFingerprint)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        AdvancementUi.renderInside(graphics, frame);
        if (!trees.isEmpty() && PsNetworking.clientSnapshot().visibleState().isPresent()) {
            renderTree(graphics, frame, mouseX, mouseY);
        }
        Component heading = trees.isEmpty()
                ? title : Component.empty().append(title).append(". ").append(currentTree().display());
        AdvancementUi.renderWindow(graphics, font, frame, heading);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (trees.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.progressiveskills.tree.unavailable"),
                    frame.contentX() + AdvancementUi.CONTENT_WIDTH / 2,
                    frame.contentY() + AdvancementUi.CONTENT_HEIGHT / 2, 0xAAAAAA);
        } else {
            renderTooltip(graphics, frame, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        if (button != 0 || !insideCanvas(frame, mouseX, mouseY)) {
            return false;
        }
        Optional<ResourceLocation> hit = hitNode((int) mouseX, (int) mouseY);
        if (hit.isPresent()) {
            selectedNode = hit.orElseThrow();
            rebuildWidgets();
        } else {
            dragging = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (dragging && button == 0) {
            panX += dragX;
            panY += dragY;
            clampPan(AdvancementUi.frame(width, height), currentTree());
            rememberPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        AdvancementUi.Frame frame = AdvancementUi.frame(width, height);
        if (insideCanvas(frame, mouseX, mouseY)) {
            panX += scrollX * 16.0D;
            panY += scrollY * 16.0D;
            clampPan(frame, currentTree());
            rememberPan();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (trees.size() > 1 && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            changeTree(Math.floorMod(treeIndex + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1), trees.size()));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        if (trees.isEmpty() || selectedNode == null || PsNetworking.clientSnapshot().visibleState().isEmpty()) {
            return Component.empty().append(title).append(". ").append(
                    Component.translatable("screen.progressiveskills.tree.unavailable"));
        }
        TreeModel tree = currentTree();
        DefinitionProjection.NodeView node = selectedNode();
        NodeStatus state = status(tree, node, PsNetworking.clientSnapshot().visibleState().orElseThrow());
        return Component.empty().append(title).append(". ").append(tree.display()).append(". ")
                .append(nodeDetails(tree, node, state));
    }

    private void addTreeTabs(AdvancementUi.Frame frame) {
        for (int index = 0; index < Math.min(24, trees.size()); index++) {
            TreeModel tree = trees.get(index);
            AdvancementTabButton.Side side = index < 8 ? AdvancementTabButton.Side.ABOVE
                    : index < 16 ? AdvancementTabButton.Side.BELOW
                    : index < 20 ? AdvancementTabButton.Side.LEFT : AdvancementTabButton.Side.RIGHT;
            int local = switch (side) {
                case ABOVE -> index;
                case BELOW -> index - 8;
                case LEFT -> index - 16;
                case RIGHT -> index - 20;
            };
            int count = switch (side) {
                case ABOVE -> Math.min(8, trees.size());
                case BELOW -> Math.min(8, Math.max(0, trees.size() - 8));
                case LEFT -> Math.min(4, Math.max(0, trees.size() - 16));
                case RIGHT -> Math.min(4, Math.max(0, trees.size() - 20));
            };
            AdvancementTabButton.Position position = local == 0
                    ? AdvancementTabButton.Position.FIRST
                    : local == count - 1 ? AdvancementTabButton.Position.LAST
                    : AdvancementTabButton.Position.MIDDLE;
            int x = switch (side) {
                case ABOVE, BELOW -> frame.x() + local * 28;
                case LEFT -> frame.x() - 28;
                case RIGHT -> frame.x() + AdvancementUi.WINDOW_WIDTH - 4;
            };
            int y = switch (side) {
                case ABOVE -> frame.y() - 28;
                case BELOW -> frame.y() + AdvancementUi.WINDOW_HEIGHT - 4;
                case LEFT, RIGHT -> frame.y() + local * 28;
            };
            int selectedIndex = index;
            addRenderableWidget(new AdvancementTabButton(
                    x, y, tree.display(), ProjectionPresentation.icon(tree.entry(), Items.KNOWLEDGE_BOOK),
                    index == treeIndex, side, position, () -> changeTree(selectedIndex)));
        }
    }

    private void renderTree(GuiGraphics graphics, AdvancementUi.Frame frame, int mouseX, int mouseY) {
        TreeModel tree = currentTree();
        VisiblePlayerState state = PsNetworking.clientSnapshot().visibleState().orElseThrow();
        nodeCenters.clear();
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            nodeCenters.put(node.id(), nodePoint(frame, node));
        }
        graphics.enableScissor(frame.contentX(), frame.contentY(), frame.contentRight(), frame.contentBottom());
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            Point target = nodeCenters.get(node.id());
            NodeStatus targetStatus = status(tree, node, state);
            for (ResourceLocation requirement : node.requires()) {
                drawConnector(graphics, nodeCenters.get(requirement), target,
                        targetStatus == NodeStatus.OWNED ? 0xFF4F913C : 0xFF666666);
            }
            for (ResourceLocation requirement : node.requiresAny()) {
                drawConnector(graphics, nodeCenters.get(requirement), target,
                        targetStatus == NodeStatus.OWNED ? 0xFF4F913C : 0xFF496A91);
            }
        }
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            Point point = nodeCenters.get(node.id());
            NodeStatus nodeStatus = status(tree, node, state);
            AdvancementUi.renderNodeFrame(graphics, point.x(), point.y(), nodeStatus.frame,
                    node.id().equals(selectedNode));
            graphics.renderItem(ProjectionPresentation.icon(node.icon(), Items.BARRIER),
                    point.x() - 8, point.y() - 8);
            Integer rank = state.nodeRanks().get(node.id());
            if (rank != null) {
                graphics.drawString(font, Component.literal(Integer.toString(rank)),
                        point.x() + 7, point.y() + 6, 0xFFFFFF, true);
            }
        }
        graphics.disableScissor();
        renderSelectionSummary(graphics, frame, tree, state);
    }

    private void renderSelectionSummary(
            GuiGraphics graphics,
            AdvancementUi.Frame frame,
            TreeModel tree,
            VisiblePlayerState state
    ) {
        DefinitionProjection.NodeView node = selectedNode();
        NodeStatus nodeStatus = status(tree, node, state);
        int left = frame.contentX() + 4;
        int top = frame.contentBottom() - 27;
        int right = frame.contentRight() - 4;
        graphics.fill(left, top, right, frame.contentBottom() - 3, 0xE0101010);
        graphics.drawString(font, Component.empty().append(component(node.display())).append(". ")
                .append(nodeStatus.label()), left + 4, top + 3, nodeStatus.color, false);
        graphics.drawString(font, Component.literal("Cost " + node.cost() + ". " + tree.tree().currency()),
                left + 4, top + 14, 0xD0D0D0, false);
    }

    private void renderTooltip(GuiGraphics graphics, AdvancementUi.Frame frame, int mouseX, int mouseY) {
        if (!insideCanvas(frame, mouseX, mouseY)) {
            return;
        }
        Optional<ResourceLocation> hit = hitNode(mouseX, mouseY);
        if (hit.isEmpty()) {
            return;
        }
        TreeModel tree = currentTree();
        DefinitionProjection.NodeView node = tree.nodes().stream()
                .filter(value -> value.id().equals(hit.orElseThrow())).findFirst().orElseThrow();
        NodeStatus state = status(tree, node, PsNetworking.clientSnapshot().visibleState().orElseThrow());
        graphics.renderTooltip(font, nodeDetails(tree, node, state), mouseX, mouseY);
    }

    private Point nodePoint(AdvancementUi.Frame frame, DefinitionProjection.NodeView node) {
        int centerX = frame.contentX() + AdvancementUi.CONTENT_WIDTH / 2;
        int centerY = frame.contentY() + AdvancementUi.CONTENT_HEIGHT / 2 - 10;
        return new Point(
                centerX + (int) Math.round(node.column() * COLUMN_SPACING + panX),
                centerY + (int) Math.round(node.row() * ROW_SPACING + panY));
    }

    private void centerTree(AdvancementUi.Frame frame, TreeModel tree) {
        int minColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maxColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maxRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        panX = -(minColumn + maxColumn) * COLUMN_SPACING / 2.0D;
        panY = -(minRow + maxRow) * ROW_SPACING / 2.0D - 8.0D;
        clampPan(frame, tree);
        rememberPan();
    }

    private void clampPan(AdvancementUi.Frame frame, TreeModel tree) {
        int minColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maxColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maxRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        double horizontal = AdvancementUi.CONTENT_WIDTH / 2.0D - 18.0D;
        double vertical = AdvancementUi.CONTENT_HEIGHT / 2.0D - 30.0D;
        panX = Math.clamp(panX,
                -maxColumn * COLUMN_SPACING - horizontal,
                -minColumn * COLUMN_SPACING + horizontal);
        panY = Math.clamp(panY,
                -maxRow * ROW_SPACING - vertical,
                -minRow * ROW_SPACING + vertical);
    }

    private void refreshModels(ClientNetworkState.Snapshot snapshot) {
        ResourceLocation previous = trees.isEmpty() ? null : currentTree().id();
        var next = new ArrayList<TreeModel>();
        snapshot.activeDefinitions().ifPresent(projection -> projection.definitions().forEach((key, entry) ->
                entry.tree().ifPresent(tree -> next.add(new TreeModel(
                        key.id(), ProjectionPresentation.display(entry, key.id().getPath()), entry,
                        tree, orderedNodes(tree.nodes()))))));
        next.sort(Comparator.comparing(TreeModel::id, ResourceLocation::compareNamespaced));
        trees = List.copyOf(next);
        if (trees.isEmpty()) {
            treeIndex = 0;
            selectedNode = null;
            return;
        }
        treeIndex = previous == null ? 0 : indexOfTree(previous);
        TreeModel tree = currentTree();
        if (selectedNode == null || tree.nodes().stream().noneMatch(node -> node.id().equals(selectedNode))) {
            selectedNode = tree.nodes().getFirst().id();
            centerRequested = true;
        }
    }

    private void changeTree(int index) {
        rememberPan();
        treeIndex = Math.clamp(index, 0, trees.size() - 1);
        selectedNode = currentTree().nodes().getFirst().id();
        Pan remembered = rememberedPans.get(currentTree().id());
        if (remembered == null) {
            centerRequested = true;
        } else {
            panX = remembered.x();
            panY = remembered.y();
            centerRequested = false;
        }
        rebuildWidgets();
    }

    private void rememberPan() {
        if (!trees.isEmpty()) {
            rememberedPans.put(currentTree().id(), new Pan(panX, panY));
        }
    }

    private void send(String label, java.util.function.BooleanSupplier sender) {
        if (!SafeRetryTray.sendOrRemember(label, sender) && minecraft != null && minecraft.player != null) {
            PsNetworking.requestClientResync("tree screen action rejected");
            minecraft.player.displayClientMessage(
                    Component.translatable("screen.progressiveskills.action_changed"), true);
        }
    }

    private Optional<NetworkPayloads.TreeRefundPreview> matchingPreview(ClientNetworkState.Snapshot snapshot) {
        if (trees.isEmpty() || selectedNode == null || snapshot.visibleState().isEmpty()) {
            return Optional.empty();
        }
        return snapshot.treeRefundPreview().filter(preview -> preview.treeId().equals(currentTree().id())
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

    private Optional<ResourceLocation> hitNode(int mouseX, int mouseY) {
        return nodeCenters.entrySet().stream()
                .filter(entry -> Math.abs(mouseX - entry.getValue().x()) <= 14
                        && Math.abs(mouseY - entry.getValue().y()) <= 14)
                .map(Map.Entry::getKey).findFirst();
    }

    private static boolean insideCanvas(AdvancementUi.Frame frame, double mouseX, double mouseY) {
        return mouseX >= frame.contentX() && mouseX < frame.contentRight()
                && mouseY >= frame.contentY() && mouseY < frame.contentBottom();
    }

    private static NodeStatus status(
            TreeModel tree,
            DefinitionProjection.NodeView node,
            VisiblePlayerState state
    ) {
        if (state.nodeRanks().containsKey(node.id())) {
            return NodeStatus.OWNED;
        }
        if (!tree.tree().enabled()) {
            return NodeStatus.SUSPENDED;
        }
        boolean required = node.requires().stream().allMatch(state.nodeRanks()::containsKey);
        boolean any = node.requiresAny().isEmpty()
                || node.requiresAny().stream().anyMatch(state.nodeRanks()::containsKey);
        boolean levels = node.minimumSkillLevels().entrySet().stream().allMatch(entry ->
                state.balances().getOrDefault(SkillStateIds.level(entry.getKey()).toString(), 0L)
                        >= entry.getValue());
        boolean currency = currencyAvailable(tree, node, state);
        return tree.tree().enabled() && !state.quarantined() && required && any && levels && currency
                ? NodeStatus.AVAILABLE : NodeStatus.LOCKED;
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

    private static Component nodeDetails(
            TreeModel tree,
            DefinitionProjection.NodeView node,
            NodeStatus status
    ) {
        Component details = Component.empty().append(component(node.display())).append(". ")
                .append(status.label()).append(". Cost ").append(Long.toString(node.cost())).append(". ")
                .append(tree.tree().currency().toString()).append(".");
        if (node.description().isPresent()) {
            details = Component.empty().append(details).append(" ").append(component(node.description().orElseThrow()));
        }
        return details;
    }

    private static Component component(DefinitionProjection.Text text) {
        return ProjectionPresentation.component(text);
    }

    private static List<DefinitionProjection.NodeView> orderedNodes(List<DefinitionProjection.NodeView> nodes) {
        return nodes.stream().sorted(Comparator.comparingInt(DefinitionProjection.NodeView::row)
                .thenComparingInt(DefinitionProjection.NodeView::column)
                .thenComparing(DefinitionProjection.NodeView::id, ResourceLocation::compareNamespaced)).toList();
    }

    private static void drawConnector(GuiGraphics graphics, Point source, Point target, int color) {
        if (source == null || target == null) {
            return;
        }
        int middleY = (source.y() + target.y()) / 2;
        graphics.vLine(source.x(), Math.min(source.y(), middleY), Math.max(source.y(), middleY), color);
        graphics.hLine(Math.min(source.x(), target.x()), Math.max(source.x(), target.x()), middleY, color);
        graphics.vLine(target.x(), Math.min(middleY, target.y()), Math.max(middleY, target.y()), color);
    }

    private static String fingerprint(ClientNetworkState.Snapshot snapshot) {
        String state = snapshot.visibleState().map(value -> value.syncRevision() + "."
                + value.stateRevision() + "." + value.nodeRanks().hashCode()).orElse("none");
        String preview = snapshot.treeRefundPreview().map(value -> value.requestId() + "."
                + value.previewDigest() + "." + value.blockers().hashCode()).orElse("none");
        String result = snapshot.lastIntentResult().map(value -> value.resultId() + "."
                + value.status()).orElse("none");
        return snapshot.phase() + "." + snapshot.activeDefinitions().hashCode()
                + "." + state + "." + preview + "." + result;
    }

    private enum NodeStatus {
        OWNED("screen.progressiveskills.tree.status.owned", 0x7CFC98, AdvancementUi.NodeFrame.OWNED),
        AVAILABLE("screen.progressiveskills.tree.status.available", 0x80C8FF, AdvancementUi.NodeFrame.AVAILABLE),
        SUSPENDED("screen.progressiveskills.tree.status.suspended", 0xAAAAAA, AdvancementUi.NodeFrame.LOCKED),
        LOCKED("screen.progressiveskills.tree.status.locked", 0xFF8888, AdvancementUi.NodeFrame.LOCKED);

        private final String key;
        private final int color;
        private final AdvancementUi.NodeFrame frame;

        NodeStatus(String key, int color, AdvancementUi.NodeFrame frame) {
            this.key = key;
            this.color = color;
            this.frame = frame;
        }

        Component label() {
            return Component.translatable(key);
        }
    }

    private record TreeModel(
            ResourceLocation id,
            Component display,
            DefinitionProjection.Entry entry,
            DefinitionProjection.TreeView tree,
            List<DefinitionProjection.NodeView> nodes
    ) {
    }

    private record Point(int x, int y) {
    }

    private record Pan(double x, double y) {
    }
}
