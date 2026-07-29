package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ProjectionPresentation;
import com.envisione.progressiveskills.client.SafeRetryTray;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
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
import net.minecraft.util.FormattedCharSequence;
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
    private static final double MIN_ZOOM = 0.65D;
    private static final double MAX_ZOOM = 1.65D;

    private final ResourceLocation initialTree;
    private List<TreeModel> trees = List.of();
    private int treeIndex;
    private ResourceLocation selectedNode;
    private double panX;
    private double panY;
    private double zoom = 1.0D;
    private final Map<ResourceLocation, Pan> rememberedPans = new LinkedHashMap<>();
    private boolean centerRequested = true;
    private boolean dragging;
    private final Map<ResourceLocation, Point> nodeCenters = new LinkedHashMap<>();
    private String snapshotFingerprint = "";
    private ProgressionUiTheme theme;

    public TreeScreen() {
        this(null);
    }

    public TreeScreen(ResourceLocation initialTree) {
        super(Component.translatable("screen.progressiveskills.tree.title"));
        this.initialTree = initialTree;
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
        theme = ProgressionUiTheme.load();
        refreshModels(snapshot);
        AdvancementUi.Frame frame = treeFrame();
        if (trees.isEmpty() || snapshot.visibleState().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(frame.right() - 100, frame.footerY(), 100, 20).build());
            snapshotFingerprint = fingerprint(snapshot);
            return;
        }
        addTreeTabs(frame);
        TreeModel tree = currentTree();
        DefinitionProjection.NodeView node = selectedNode();
        VisiblePlayerState state = snapshot.visibleState().orElseThrow();
        NodeStatus selectedStatus = status(tree, node, state);
        Optional<NetworkPayloads.TreeRefundPreview> preview = matchingPreview(snapshot);
        int buttonWidth = 72;
        int gap = 2;
        int x = frame.x();
        int y = treeFooterY(frame);
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
                .bounds(frame.right() - 78, y, 78, 20).build());
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
        AdvancementUi.Frame frame = treeFrame();
        AdvancementUi.renderInside(graphics, frame);
        Canvas canvas = canvas(frame);
        AdvancementUi.renderInset(graphics, canvas.left(), canvas.top(), canvas.right(), canvas.bottom());
        AdvancementUi.renderInset(graphics, canvas.right() + 3, frame.contentY() + 2,
                frame.contentRight() - 2, frame.contentBottom() - 2);
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
                    canvas.centerX(), canvas.centerY(), 0xAAAAAA);
        } else {
            renderTooltip(graphics, frame, mouseX, mouseY);
            graphics.drawCenteredString(font, Component.translatable(
                            "screen.progressiveskills.tree.zoom", Math.round(zoom * 100.0D)),
                    frame.right() - 145, treeFooterY(frame) + 6, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        AdvancementUi.Frame frame = treeFrame();
        if (button != 0 || !insideCanvas(canvas(frame), mouseX, mouseY)) {
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
            panX += dragX / zoom;
            panY += dragY / zoom;
            clampPan(treeFrame(), currentTree());
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
        AdvancementUi.Frame frame = treeFrame();
        Canvas canvas = canvas(frame);
        if (insideCanvas(canvas, mouseX, mouseY)) {
            double previous = zoom;
            zoom = Math.clamp(zoom + scrollY * 0.1D, MIN_ZOOM, MAX_ZOOM);
            double relativeX = mouseX - canvas.centerX();
            double relativeY = mouseY - canvas.centerY();
            panX += relativeX / zoom - relativeX / previous;
            panY += relativeY / zoom - relativeY / previous;
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
        if (keyCode == GLFW.GLFW_KEY_SPACE && !trees.isEmpty()) {
            centerTree(treeFrame(), currentTree());
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
                case RIGHT -> frame.right() - 4;
            };
            int y = switch (side) {
                case ABOVE -> frame.y() - 28;
                case BELOW -> frame.bottom() - 4;
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
        Canvas canvas = canvas(frame);
        nodeCenters.clear();
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            nodeCenters.put(node.id(), nodePoint(frame, node));
        }
        graphics.enableScissor(canvas.left() + 1, canvas.top() + 1, canvas.right() - 1, canvas.bottom() - 1);
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
        Canvas canvas = canvas(frame);
        int left = canvas.right() + 7;
        int top = frame.contentY() + 7;
        int right = frame.contentRight() - 4;
        graphics.renderItem(ProjectionPresentation.icon(node.icon(), Items.BARRIER), left + 3, top + 2);
        int textX = left + 24;
        int textWidth = Math.max(30, right - textX - 3);
        int y = top + 2;
        for (var line : font.split(UiText.legacy(component(node.display()).getString()), textWidth)) {
            graphics.drawString(font, line, textX, y, 0xFFFFFF, false);
            y += font.lineHeight + 1;
        }
        y = Math.max(y + 2, top + 22);
        graphics.drawString(font, nodeStatus.label(), left + 3, y, nodeStatus.color, false);
        y += font.lineHeight + 3;
        Component cost = Component.literal("Cost " + node.cost() + " ")
                .append(currencyDisplay(tree.tree().currency()));
        for (var line : font.split(cost, Math.max(40, right - left - 6))) {
            graphics.drawString(font, line, left + 3, y, 0xFFD0D0D0, false);
            y += font.lineHeight + 1;
        }
        if (node.description().isPresent()) {
            y += 4;
            int bottom = frame.contentBottom() - 5;
            for (var line : font.split(UiText.legacy(component(node.description().orElseThrow()).getString()),
                    Math.max(40, right - left - 6))) {
                if (y + font.lineHeight > bottom) {
                    break;
                }
                graphics.drawString(font, line, left + 3, y, 0xFFB8B8B8, false);
                y += font.lineHeight + 1;
            }
        }
    }

    private void renderTooltip(GuiGraphics graphics, AdvancementUi.Frame frame, int mouseX, int mouseY) {
        if (!insideCanvas(canvas(frame), mouseX, mouseY)) {
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
        renderBoundedTooltip(graphics, tooltipLines(tree, node, state), mouseX, mouseY);
    }

    private Point nodePoint(AdvancementUi.Frame frame, DefinitionProjection.NodeView node) {
        Canvas canvas = canvas(frame);
        return new Point(
                canvas.centerX() + (int) Math.round((node.column() * COLUMN_SPACING + panX) * zoom),
                canvas.centerY() + (int) Math.round((node.row() * ROW_SPACING + panY) * zoom));
    }

    private void centerTree(AdvancementUi.Frame frame, TreeModel tree) {
        int minColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maxColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maxRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        panX = -(minColumn + maxColumn) * COLUMN_SPACING / 2.0D;
        panY = -(minRow + maxRow) * ROW_SPACING / 2.0D;
        zoom = fitZoom(frame, tree);
        clampPan(frame, tree);
        rememberPan();
    }

    private void clampPan(AdvancementUi.Frame frame, TreeModel tree) {
        int minColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maxColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maxRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        Canvas canvas = canvas(frame);
        double horizontal = canvas.width() / (2.0D * zoom) - 18.0D;
        double vertical = canvas.height() / (2.0D * zoom) - 18.0D;
        panX = Math.clamp(panX,
                -maxColumn * COLUMN_SPACING - horizontal,
                -minColumn * COLUMN_SPACING + horizontal);
        panY = Math.clamp(panY,
                -maxRow * ROW_SPACING - vertical,
                -minRow * ROW_SPACING + vertical);
    }

    private double fitZoom(AdvancementUi.Frame frame, TreeModel tree) {
        int minColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maxColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maxRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        Canvas canvas = canvas(frame);
        double graphWidth = Math.max(44.0D, (maxColumn - minColumn) * COLUMN_SPACING + 44.0D);
        double graphHeight = Math.max(44.0D, (maxRow - minRow) * ROW_SPACING + 44.0D);
        return Math.clamp(Math.min(canvas.width() / graphWidth, canvas.height() / graphHeight),
                MIN_ZOOM, 1.0D);
    }

    private List<Component> tooltipLines(
            TreeModel tree,
            DefinitionProjection.NodeView node,
            NodeStatus status
    ) {
        String description = node.description().map(TreeScreen::component)
                .map(Component::getString).orElse("");
        Map<String, String> replacements = Map.of(
                "{name}", component(node.display()).getString(),
                "{state}", status.label().getString(),
                "{cost}", Long.toString(node.cost()),
                "{currency}", currencyDisplay(tree.tree().currency()).getString(),
                "{description}", description,
                "{tree}", tree.display().getString(),
                "{node_id}", UiText.prettyId(node.id())
        );
        var result = new ArrayList<Component>();
        for (String template : theme.treeTooltipLines()) {
            String rendered = template;
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                rendered = rendered.replace(replacement.getKey(), replacement.getValue());
            }
            if (rendered.isEmpty() && template.contains("{description}")) {
                continue;
            }
            for (String line : rendered.split("\\n", -1)) {
                result.add(UiText.legacy(line.isEmpty() ? " " : line));
            }
        }
        return List.copyOf(result);
    }

    private Component currencyDisplay(ResourceLocation currency) {
        return PsNetworking.clientSnapshot().activeDefinitions().stream()
                .flatMap(value -> value.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CURRENCY)
                        && entry.getKey().id().equals(currency))
                .map(Map.Entry::getValue)
                .map(entry -> ProjectionPresentation.display(entry, UiText.prettyId(currency)))
                .map(value -> UiText.legacy(value.getString()))
                .findFirst().orElseGet(() -> Component.literal(UiText.prettyId(currency)));
    }

    private void renderBoundedTooltip(
            GuiGraphics graphics,
            List<Component> components,
            int mouseX,
            int mouseY
    ) {
        int maxWidth = Math.max(60, Math.min(theme.treeTooltipWidth(), width - 24));
        var lines = new ArrayList<FormattedCharSequence>();
        for (Component component : components) {
            lines.addAll(font.split(component, maxWidth));
        }
        int maxLines = Math.max(1, (height - 24) / (font.lineHeight + 1));
        if (lines.size() > maxLines) {
            lines.subList(maxLines - 1, lines.size()).clear();
            lines.add(FormattedCharSequence.forward("...", net.minecraft.network.chat.Style.EMPTY));
        }
        int textWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int tooltipWidth = textWidth + 8;
        int tooltipHeight = lines.size() * (font.lineHeight + 1) + 7;
        int preferredX = mouseX + 14;
        if (preferredX + tooltipWidth > width - 6) {
            preferredX = mouseX - tooltipWidth - 14;
        }
        int x = Math.clamp(preferredX, 6, width - tooltipWidth - 6);
        int y = Math.clamp(mouseY + 12, 6, height - tooltipHeight - 6);
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xF0100010);
        graphics.hLine(x, x + tooltipWidth - 1, y, 0xFF5000A0);
        graphics.vLine(x, y, y + tooltipHeight - 1, 0xFF5000A0);
        graphics.hLine(x, x + tooltipWidth - 1, y + tooltipHeight - 1, 0xFF280050);
        graphics.vLine(x + tooltipWidth - 1, y, y + tooltipHeight - 1, 0xFF280050);
        int lineY = y + 4;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + 4, lineY, 0xFFFFFFFF, true);
            lineY += font.lineHeight + 1;
        }
    }

    private static Canvas canvas(AdvancementUi.Frame frame) {
        int sidebarWidth = Math.clamp(frame.contentWidth() / 3, 104, 170);
        return new Canvas(
                frame.contentX() + 2,
                frame.contentY() + 2,
                frame.contentRight() - sidebarWidth - 5,
                frame.contentBottom() - 2
        );
    }

    private AdvancementUi.Frame treeFrame() {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(width, height);
        if (trees.size() <= 8 || frame.height() - 28 < AdvancementUi.WINDOW_HEIGHT) {
            return frame;
        }
        return new AdvancementUi.Frame(frame.x(), frame.y(), frame.width(), frame.height() - 28);
    }

    private int treeFooterY(AdvancementUi.Frame frame) {
        return trees.size() > 8 && frame.height() < AdvancementUi.largeFrame(width, height).height()
                ? frame.bottom() + 30 : frame.footerY();
    }

    private void refreshModels(ClientNetworkState.Snapshot snapshot) {
        ResourceLocation previous = trees.isEmpty() ? initialTree : currentTree().id();
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
            zoom = remembered.zoom();
            centerRequested = false;
        }
        rebuildWidgets();
    }

    private void rememberPan() {
        if (!trees.isEmpty()) {
            rememberedPans.put(currentTree().id(), new Pan(panX, panY, zoom));
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

    private static boolean insideCanvas(Canvas canvas, double mouseX, double mouseY) {
        return mouseX >= canvas.left() && mouseX < canvas.right()
                && mouseY >= canvas.top() && mouseY < canvas.bottom();
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
                .append(UiText.prettyId(tree.tree().currency())).append(".");
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

    private record Canvas(int left, int top, int right, int bottom) {
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
    }

    private record Pan(double x, double y, double zoom) {
    }
}
