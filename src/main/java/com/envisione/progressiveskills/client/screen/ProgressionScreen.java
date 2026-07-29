package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ClientPreferences;
import com.envisione.progressiveskills.client.ProjectionPresentation;
import com.envisione.progressiveskills.client.SafeRetryTray;
import com.envisione.progressiveskills.client.TestCenterState;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.network.ClientNetworkState;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.NetworkPayloads;
import com.envisione.progressiveskills.common.network.PsNetworking;
import com.envisione.progressiveskills.common.network.VisiblePlayerState;
import com.envisione.progressiveskills.common.skill.FixedPoint;
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProgressionScreen extends ProgressiveScreen {
    private static final List<Tab> NAVIGATION_TABS = List.of(
            Tab.MAIN,
            Tab.SKILLS,
            Tab.CLASSES,
            Tab.ABILITIES
    );
    private static final double MIN_TREE_ZOOM = 0.65D;
    private static final double MAX_TREE_ZOOM = 1.65D;
    private static final int TREE_COLUMN_SPACING = 58;
    private static final int TREE_ROW_SPACING = 48;

    private Tab tab;
    private int page;
    private int selected;
    private ResourceLocation compareFirst;
    private ResourceLocation compareSecond;
    private List<Row> rows = List.of();
    private final List<ProgressionCardButton> cards = new ArrayList<>();
    private final List<ProgressionSelectorButton> selectors = new ArrayList<>();
    private List<ClassSlotModel> classSlots = List.of();
    private ResourceLocation selectedClassSlot;
    private int classSlotPage;
    private String fingerprint = "";
    private String query = "";
    private ProgressionUiTheme theme;
    private Map<ResourceLocation, ProgressionWorkbenchLayout.Point> skillNodeCenters = Map.of();
    private final Map<ResourceLocation, TreeViewport> treeViewports = new HashMap<>();
    private ResourceLocation inspectedSkillNode;
    private ResourceLocation renderedTreeId;
    private int renderedSkillIndex = -1;
    private boolean draggingTree;
    private int lastMouseX;
    private int lastMouseY;

    public ProgressionScreen() {
        this(Tab.MAIN);
    }

    public ProgressionScreen(Tab initialTab) {
        super(Component.translatable("screen.progressiveskills.progression.title"));
        tab = initialTab;
    }

    public static boolean isAvailable(ClientNetworkState.Snapshot snapshot) {
        return snapshot.phase() == ClientNetworkState.ClientPhase.ACTIVE;
    }

    @Override
    protected void init() {
        var snapshot = PsNetworking.clientSnapshot();
        theme = ProgressionUiTheme.load();
        cards.clear();
        selectors.clear();
        if (tab == Tab.CLASSES) {
            refreshClassSlots(snapshot);
        }
        rows = rows(snapshot);
        selected = rows.isEmpty() ? 0 : Math.max(0, Math.min(selected, rows.size() - 1));
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(width, height);
        int visibleEntries = visibleEntries(frame);
        int pages = Math.max(1, (rows.size() + visibleEntries - 1) / visibleEntries);
        page = Math.max(0, Math.min(page, pages - 1));
        addProgressionTabs(frame);
        if (tab == Tab.MAIN) {
            addMainMenu(frame);
        } else if (tab == Tab.SKILLS) {
            addSkillSelectors(snapshot, frame);
        } else if (tab == Tab.CLASSES) {
            addClassSelectors(snapshot, frame);
        } else {
            addCardBrowser(snapshot, frame);
        }
        int bottom = frame.footerY();
        if (tab != Tab.MAIN) {
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                    .bounds(frame.x(), bottom, 24, 20).build());
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                    .bounds(frame.x() + 26, bottom, 24, 20).build());
            next.active = page + 1 < pages;
        }
        addActions(snapshot, bottom);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(frame.right() - 100, bottom, 100, 20).build());
        fingerprint = fingerprint(snapshot);
    }

    private void addProgressionTabs(AdvancementUi.Frame frame) {
        for (int index = 0; index < navigationTabs().size(); index++) {
            Tab value = navigationTabs().get(index);
            AdvancementTabButton.Position position = index == 0
                    ? AdvancementTabButton.Position.FIRST
                    : index == navigationTabs().size() - 1 ? AdvancementTabButton.Position.LAST
                    : AdvancementTabButton.Position.MIDDLE;
            addRenderableWidget(new AdvancementTabButton(
                    frame.x() + index * 28,
                    frame.y() - 28,
                    Component.literal(value.label),
                    tabIcon(value),
                    value == tab,
                    AdvancementTabButton.Side.ABOVE,
                    position,
                    () -> selectTab(value)));
        }
    }

    static List<Tab> navigationTabs() {
        return NAVIGATION_TABS;
    }

    private void addMainMenu(AdvancementUi.Frame frame) {
        int gap = 6;
        int cardWidth = Math.max(80, (frame.contentWidth() - gap * 4) / 3);
        int cardHeight = Math.min(64, Math.max(42, frame.contentHeight() / 3));
        int left = frame.contentX() + gap;
        int top = frame.contentY() + Math.max(32, frame.contentHeight() / 2 - cardHeight / 2);
        List<Tab> destinations = List.of(Tab.SKILLS, Tab.CLASSES, Tab.ABILITIES);
        for (int index = 0; index < destinations.size(); index++) {
            Tab destination = destinations.get(index);
            ProgressionCardButton button = addRenderableWidget(new ProgressionCardButton(
                    left + index * (cardWidth + gap),
                    top,
                    cardWidth,
                    cardHeight,
                    index,
                    Component.literal(destination.label),
                    Component.translatable("screen.progressiveskills.main.open"),
                    tabIcon(destination),
                    false,
                    () -> selectTab(destination)
            ));
            cards.add(button);
        }
    }

    private void addCardBrowser(ClientNetworkState.Snapshot snapshot, AdvancementUi.Frame frame) {
        if (searchableTab()) {
            int searchWidth = Math.min(180, cardAreaWidth(frame) - 8);
            EditBox search = new EditBox(font, frame.contentX() + 4, frame.contentY() + 4, searchWidth, 18,
                    Component.literal("Search progression definitions"));
            search.setHint(Component.literal("Search"));
            search.setValue(query);
            search.setResponder(value -> {
                if (!query.equals(value)) {
                    query = value;
                    page = 0;
                    selected = 0;
                    rebuildWidgets();
                }
            });
            addRenderableWidget(search);
        }
        int contentTop = frame.contentY() + (searchableTab() ? 27 : 5);
        int columns = cardColumns(frame);
        int cardGap = 4;
        int cardHeight = 40;
        int cardWidth = (cardAreaWidth(frame) - 8 - (columns - 1) * cardGap) / columns;
        int visibleRows = visibleEntries(frame);
        int first = page * visibleRows;
        int last = Math.min(rows.size(), first + visibleRows);
        for (int index = first; index < last; index++) {
            Row row = rows.get(index);
            int rowIndex = index;
            int cell = index - first;
            int column = cell % columns;
            int localRow = cell / columns;
            ProgressionCardButton button = addRenderableWidget(new ProgressionCardButton(
                    frame.contentX() + 4 + column * (cardWidth + cardGap),
                    contentTop + localRow * (cardHeight + cardGap),
                    cardWidth,
                    cardHeight,
                    rowIndex,
                    UiText.legacy(row.label()),
                    Component.literal(cardSummary(row)),
                    rowIcon(snapshot, row),
                    index == selected,
                    () -> {
                        selected = rowIndex;
                        rebuildWidgets();
                    }));
            cards.add(button);
        }
    }

    private void addSkillSelectors(ClientNetworkState.Snapshot snapshot, AdvancementUi.Frame frame) {
        ProgressionWorkbenchLayout.Layout layout = ProgressionWorkbenchLayout.calculate(frame);
        int buttonSize = 26;
        int gap = 3;
        int visible = visibleEntries(frame);
        int first = page * visible;
        int last = Math.min(rows.size(), first + visible);
        for (int index = first; index < last; index++) {
            Row row = rows.get(index);
            int rowIndex = index;
            long level = row.id().map(id -> skillLevel(snapshot, id)).orElse(0L);
            ProgressionSelectorButton button = addRenderableWidget(new ProgressionSelectorButton(
                    layout.selector().left() + 2,
                    layout.selector().top() + 4 + (index - first) * (buttonSize + gap),
                    buttonSize,
                    buttonSize,
                    rowIndex,
                    UiText.legacy(row.label()),
                    Component.literal(cardSummary(row)),
                    rowIcon(snapshot, row),
                    Long.toString(level),
                    index == selected,
                    () -> {
                        selected = rowIndex;
                        inspectedSkillNode = null;
                        rebuildWidgets();
                    }
            ));
            selectors.add(button);
        }
    }

    private void addClassSelectors(ClientNetworkState.Snapshot snapshot, AdvancementUi.Frame frame) {
        int slotWidth = classSlotWidth(frame);
        int slotButtonWidth = slotWidth - 8;
        int slotVisible = visibleClassSlots(frame);
        int slotPages = Math.max(1, (classSlots.size() + slotVisible - 1) / slotVisible);
        classSlotPage = Math.max(0, Math.min(classSlotPage, slotPages - 1));
        int slotFirst = classSlotPage * slotVisible;
        int slotLast = Math.min(classSlots.size(), slotFirst + slotVisible);
        for (int index = slotFirst; index < slotLast; index++) {
            ClassSlotModel slot = classSlots.get(index);
            int used = usedClassCapacity(snapshot, slot.id());
            addRenderableWidget(new ProgressionSelectorButton(
                    frame.contentX() + 5,
                    frame.contentY() + 6 + (index - slotFirst) * 29,
                    slotButtonWidth,
                    26,
                    -1,
                    slot.display(),
                    Component.literal("Capacity " + used + " of " + slot.view().capacity()),
                    ProjectionPresentation.icon(slot.entry(), Items.CHEST),
                    used + "/" + slot.view().capacity(),
                    slot.id().equals(selectedClassSlot),
                    () -> selectClassSlot(slot.id())
            ));
        }
        if (slotPages > 1) {
            int navY = frame.contentBottom() - 22;
            int navWidth = (slotButtonWidth - 2) / 2;
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"),
                            ignored -> changeClassSlotPage(-1))
                    .bounds(frame.contentX() + 5, navY, navWidth, 18).build());
            previous.active = classSlotPage > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"),
                            ignored -> changeClassSlotPage(1))
                    .bounds(frame.contentX() + 7 + navWidth, navY, navWidth, 18).build());
            next.active = classSlotPage + 1 < slotPages;
        }
        int gridLeft = frame.contentX() + slotWidth + 4;
        int gridTop = frame.contentY() + 28;
        int buttonSize = 36;
        int gap = 5;
        int columns = classColumns(frame);
        int visible = visibleEntries(frame);
        int first = page * visible;
        int last = Math.min(rows.size(), first + visible);
        for (int index = first; index < last; index++) {
            Row row = rows.get(index);
            int cell = index - first;
            int rowIndex = index;
            boolean owned = row.id().stream().anyMatch(id -> snapshot.visibleState().stream()
                    .anyMatch(state -> state.selectedClasses().containsKey(id)));
            int weight = row.id().flatMap(id -> classView(snapshot, id))
                    .map(DefinitionProjection.ClassView::slotCost).orElse(0);
            ProgressionSelectorButton button = addRenderableWidget(new ProgressionSelectorButton(
                    gridLeft + cell % columns * (buttonSize + gap),
                    gridTop + cell / columns * (buttonSize + gap),
                    buttonSize,
                    buttonSize,
                    rowIndex,
                    UiText.legacy(row.label()),
                    Component.literal(cardSummary(row)),
                    rowIcon(snapshot, row),
                    owned ? "ON" : Integer.toString(weight),
                    index == selected,
                    () -> {
                        selected = rowIndex;
                        rebuildWidgets();
                    }
            ));
            selectors.add(button);
        }
    }

    @Override
    public void tick() {
        super.tick();
        String current = fingerprint(PsNetworking.clientSnapshot());
        if (!current.equals(fingerprint)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(width, height);
        AdvancementUi.renderInside(graphics, frame);
        if (tab == Tab.MAIN) {
            renderMainMenu(graphics, frame, PsNetworking.clientSnapshot());
        } else if (tab == Tab.SKILLS) {
            renderSkillDashboard(graphics, frame, PsNetworking.clientSnapshot(), mouseX, mouseY);
        } else if (tab == Tab.CLASSES) {
            renderClassDashboard(graphics, frame, PsNetworking.clientSnapshot());
        } else {
            int detailsLeft = detailLeft(frame);
            AdvancementUi.renderInset(graphics, frame.contentX() + 2, frame.contentY() + 2,
                    detailsLeft - 3, frame.contentBottom() - 2);
            AdvancementUi.renderInset(graphics, detailsLeft, frame.contentY() + 2,
                    frame.contentRight() - 2, frame.contentBottom() - 2);
            graphics.enableScissor(detailsLeft + 1, frame.contentY() + 3,
                    frame.contentRight() - 3, frame.contentBottom() - 3);
            renderDetails(graphics, PsNetworking.clientSnapshot());
            graphics.disableScissor();
        }
        AdvancementUi.renderWindow(graphics, font, frame,
                Component.empty().append(title).append(". ").append(tab.label));
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tab != Tab.MAIN) {
            int pages = Math.max(1, (rows.size() + visibleEntries(frame) - 1) / visibleEntries(frame));
            int pageCenter = tab == Tab.CLASSES
                    ? frame.contentX() + classSlotWidth(frame) + Math.max(20,
                    (detailLeft(frame) - frame.contentX() - classSlotWidth(frame)) / 2)
                    : tab == Tab.SKILLS ? frame.contentX() + frame.contentWidth() / 2
                    : frame.contentX() + cardAreaWidth(frame) / 2;
            graphics.drawCenteredString(font, Component.literal((page + 1) + " of " + pages),
                    pageCenter, frame.footerY() + 6, 0xFFFFFF);
        }
        if (tab == Tab.SKILLS) {
            renderSkillTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderMainMenu(
            GuiGraphics graphics,
            AdvancementUi.Frame frame,
            ClientNetworkState.Snapshot snapshot
    ) {
        renderTiledBackground(
                graphics,
                new ProgressionWorkbenchLayout.Rect(
                        frame.contentX(),
                        frame.contentY(),
                        frame.contentRight(),
                        frame.contentBottom()
                ),
                theme.workbenchBackground()
        );
        int insetLeft = frame.contentX() + 8;
        int insetTop = frame.contentY() + 8;
        int insetRight = frame.contentRight() - 8;
        int insetBottom = frame.contentY() + 56;
        AdvancementUi.renderInset(graphics, insetLeft, insetTop, insetRight, insetBottom);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.progressiveskills.main.heading"),
                (insetLeft + insetRight) / 2,
                insetTop + 8,
                0xFFFFD65C
        );
        long totalLevel = snapshot.visibleState().stream()
                .flatMap(state -> state.balances().entrySet().stream())
                .filter(entry -> entry.getKey().startsWith("progressiveskills:skill_level/"))
                .mapToLong(Map.Entry::getValue)
                .sum();
        int skills = definitionCount(snapshot, DefinitionKinds.SKILL);
        int classes = snapshot.visibleState().map(state -> state.selectedClasses().size()).orElse(0);
        int abilities = snapshot.visibleState().map(state -> state.abilities().size()).orElse(0);
        Component summary = Component.translatable(
                "screen.progressiveskills.main.summary",
                totalLevel,
                skills,
                classes,
                abilities
        );
        graphics.drawCenteredString(
                font,
                summary,
                (insetLeft + insetRight) / 2,
                insetTop + 27,
                0xFFB8B8B8
        );
    }

    private static int definitionCount(
            ClientNetworkState.Snapshot snapshot,
            com.envisione.progressiveskills.common.id.DefinitionKind kind
    ) {
        return snapshot.activeDefinitions().stream()
                .flatMap(definitions -> definitions.definitions().keySet().stream())
                .filter(key -> key.kind().equals(kind))
                .mapToInt(ignored -> 1)
                .sum();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.SKILLS && button == 0 && skillGraph().contains(mouseX, mouseY)) {
            Optional<ResourceLocation> clicked = skillNodeCenters.entrySet().stream()
                    .filter(entry -> {
                        double x = mouseX - entry.getValue().x();
                        double y = mouseY - entry.getValue().y();
                        return x * x + y * y <= 15.0D * 15.0D;
                    })
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (clicked.isPresent()) {
                inspectedSkillNode = clicked.orElseThrow();
                if (renderedSkillIndex >= 0 && renderedSkillIndex < rows.size()
                        && renderedSkillIndex != selected) {
                    selected = renderedSkillIndex;
                }
                rebuildWidgets();
                return true;
            }
            if (renderedTreeId != null) {
                draggingTree = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (draggingTree && button == 0 && renderedTreeId != null) {
            Optional<SkillTreeModel> tree = treeById(PsNetworking.clientSnapshot(), renderedTreeId);
            if (tree.isPresent()) {
                TreeViewport viewport = treeViewports.get(renderedTreeId);
                viewport.panX += dragX / viewport.zoom;
                viewport.panY += dragY / viewport.zoom;
                clampTreeViewport(viewport, tree.orElseThrow().tree(), skillGraph());
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingTree) {
            draggingTree = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ProgressionWorkbenchLayout.Rect graph = skillGraph();
        if (tab == Tab.SKILLS && renderedTreeId != null && graph.contains(mouseX, mouseY)) {
            Optional<SkillTreeModel> tree = treeById(PsNetworking.clientSnapshot(), renderedTreeId);
            if (tree.isPresent()) {
                TreeViewport viewport = treeViewports.get(renderedTreeId);
                ProgressionWorkbenchLayout.Viewport zoomed = ProgressionWorkbenchLayout.zoomAround(
                        new ProgressionWorkbenchLayout.Viewport(
                                viewport.panX,
                                viewport.panY,
                                viewport.zoom
                        ),
                        scrollY,
                        mouseX,
                        mouseY,
                        graph.centerX(),
                        graph.centerY(),
                        MIN_TREE_ZOOM,
                        MAX_TREE_ZOOM
                );
                viewport.panX = zoomed.panX();
                viewport.panY = zoomed.panY();
                viewport.zoom = zoomed.zoom();
                clampTreeViewport(viewport, tree.orElseThrow().tree(), graph);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ProgressionWorkbenchLayout.Rect graph = skillGraph();
        if (tab == Tab.SKILLS
                && keyCode == GLFW.GLFW_KEY_SPACE
                && renderedTreeId != null
                && graph.contains(lastMouseX, lastMouseY)) {
            treeById(PsNetworking.clientSnapshot(), renderedTreeId).ifPresent(tree ->
                    centerTreeViewport(
                            treeViewports.computeIfAbsent(renderedTreeId, ignored -> new TreeViewport()),
                            tree.tree(),
                            graph
                    ));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        String detail = rows.isEmpty() ? "No entries." : rows.get(selected).detail();
        if (tab == Tab.SKILLS && inspectedSkillNode != null) {
            ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
            Optional<DefinitionProjection.NodeView> node = selectedRow().flatMap(Row::id)
                    .flatMap(id -> boundTree(snapshot, id))
                    .flatMap(tree -> tree.tree().nodes().stream()
                            .filter(value -> value.id().equals(inspectedSkillNode)).findFirst());
            if (node.isPresent()) {
                DefinitionProjection.NodeView value = node.orElseThrow();
                detail += ". " + ProjectionPresentation.component(value.display()).getString();
                detail += value.description().map(ProjectionPresentation::component)
                        .map(Component::getString).map(text -> ". " + text).orElse("");
            }
        }
        return Component.literal(title.getString() + ". " + tab.label + ". " + detail);
    }

    private void addActions(ClientNetworkState.Snapshot snapshot, int y) {
        if (tab == Tab.SKILLS) {
            ProgressionWorkbenchLayout.Rect details = ProgressionWorkbenchLayout.calculate(
                    AdvancementUi.largeFrame(width, height)).details();
            if (details.height() >= 80) {
                selectedRow().flatMap(Row::id).flatMap(id -> boundTree(snapshot, id)).ifPresent(tree ->
                        selectedSkillNode(tree.tree()).ifPresent(node ->
                                snapshot.visibleState().ifPresent(state -> {
                                    SkillNodeStatus status = skillNodeStatus(tree.tree(), node, state);
                                    Optional<NetworkPayloads.TreeRefundPreview> preview =
                                            matchingTreeRefundPreview(snapshot, tree.id(), node.id());
                                    int x = details.left() + 3;
                                    int width = Math.max(48, details.width() - 6);
                                    if (status == SkillNodeStatus.AVAILABLE) {
                                        addAction(x, details.bottom() - 21, "Buy", () -> safe(
                                                "Buy tree node " + node.id(),
                                                () -> PsNetworking.sendTreeBuy(tree.id(), node.id())
                                        ), width);
                                    } else if (status == SkillNodeStatus.OWNED
                                            && preview.filter(NetworkPayloads.TreeRefundPreview::allowed).isPresent()) {
                                        String digest = preview.orElseThrow().previewDigest();
                                        addAction(x, details.bottom() - 21, "Confirm refund", () -> safe(
                                                "Confirm tree refund " + node.id(),
                                                () -> PsNetworking.sendTreeRefundConfirm(
                                                        tree.id(),
                                                        node.id(),
                                                        digest
                                                )
                                        ), width);
                                    } else if (status == SkillNodeStatus.OWNED) {
                                        addAction(x, details.bottom() - 21, "Preview refund", () -> safe(
                                                "Preview tree refund " + node.id(),
                                                () -> PsNetworking.sendTreeRefundPreview(tree.id(), node.id())
                                        ), width);
                                    }
                                })));
            }
        } else if (tab == Tab.CLASSES && selectedRow().flatMap(Row::id).isPresent()) {
            ResourceLocation id = selectedRow().flatMap(Row::id).orElseThrow();
            boolean owned = snapshot.visibleState().stream().anyMatch(state -> state.selectedClasses().containsKey(id));
            Optional<DefinitionProjection.ClassView> classView = classView(snapshot, id);
            if (!owned && classView.filter(DefinitionProjection.ClassView::enabled).isEmpty()) {
                return;
            }
            Optional<NetworkPayloads.ClassChangePreview> preview = matchingClassPreview(snapshot, id);
            if (owned && preview.filter(NetworkPayloads.ClassChangePreview::allowed).isPresent()) {
                String digest = preview.orElseThrow().previewDigest();
                addActionSlot(0, y, "Confirm respec", () -> safe(
                        "Confirm class respec", () -> PsNetworking.sendClassRespecConfirm(id, digest)));
            } else {
                addActionSlot(0, y, owned ? "Preview respec" : "Select", () -> {
                    if (owned) {
                        safe("Preview class respec", () -> PsNetworking.sendClassRespecPreview(id));
                    } else {
                        safe("Select class", () -> PsNetworking.sendClassSelect(id));
                    }
                });
            }
        } else if (tab == Tab.ABILITIES && selectedRow().flatMap(Row::id).isPresent()) {
            ResourceLocation id = selectedRow().flatMap(Row::id).orElseThrow();
            Optional<DefinitionProjection.AbilityView> ability = abilityView(snapshot, id);
            Optional<Integer> assigned = assignedSlot(snapshot.visibleState(), id);
            boolean owned = snapshot.visibleState().stream()
                    .anyMatch(state -> state.abilities().containsKey(id));
            int action = 0;
            if (owned && ability.filter(value -> value.kind().equals("toggle")).isPresent()) {
                addActionSlot(action++, y, "Toggle", () -> safe(
                        "Toggle " + id, () -> PsNetworking.sendAbilityToggle(id)));
            } else if (owned && ability.filter(value -> value.kind().equals("active")).isPresent()
                    && assigned.isPresent()) {
                int slot = assigned.orElseThrow();
                addActionSlot(action++, y, "Activate", () -> safe(
                        "Activate " + id, () -> PsNetworking.sendAbilityActivate(slot)));
            }
            if (owned && assigned.isPresent()) {
                int slot = assigned.orElseThrow();
                addActionSlot(action, y, "Unassign", () -> safe(
                        "Unassign " + id, () -> PsNetworking.sendAbilityUnassign(slot)));
            } else if (owned && ability.filter(DefinitionProjection.AbilityView::slotAllowed).isPresent()) {
                Optional<Integer> free = firstFreeSlot(snapshot.visibleState());
                if (free.isPresent()) {
                    int slot = free.orElseThrow();
                    addActionSlot(action, y, "Assign", () -> safe(
                            "Assign " + id, () -> PsNetworking.sendAbilityAssign(id, slot)));
                }
            }
        } else if (tab == Tab.CLAIMS) {
            Optional<java.util.UUID> claim = selectedRow().flatMap(Row::claimId);
            int action = 0;
            if (claim.isPresent()) {
                java.util.UUID claimId = claim.orElseThrow();
                addActionSlot(action++, y, "Take", () -> safe(
                        "Take claim", () -> PsNetworking.sendClaimTake(claimId)));
            } else if (snapshot.visibleState().flatMap(state -> state.carriers().held()).isPresent()) {
                addActionSlot(action++, y, "Inspect", () -> safe(
                        "Inspect carrier", PsNetworking::sendCarrierInspect));
                Optional<NetworkPayloads.CarrierMigrationPreview> preview = snapshot.carrierMigrationPreview();
                if (preview.filter(NetworkPayloads.CarrierMigrationPreview::allowed).isPresent()) {
                    String digest = preview.orElseThrow().previewDigest();
                    addActionSlot(action++, y, "Confirm migration", () -> safe(
                            "Confirm carrier migration", () -> PsNetworking.sendCarrierMigrateConfirm(digest)));
                } else {
                    addActionSlot(action++, y, "Preview migration", () -> safe(
                            "Preview carrier migration", PsNetworking::sendCarrierMigratePreview));
                }
            }
            if (snapshot.visibleState().stream().anyMatch(state -> !state.carriers().pendingClaims().isEmpty())) {
                addActionSlot(action, y, "Take all", () -> safe(
                        "Take all claims", PsNetworking::sendClaimTakeAll));
            }
        } else if (tab == Tab.COMPARE) {
            addActionSlot(0, y, "Plan one", () -> cycleCompare(true, snapshot));
            addActionSlot(1, y, "Plan two", () -> cycleCompare(false, snapshot));
        } else if (tab == Tab.TESTS && !rows.isEmpty()) {
            addActionSlot(0, y, "Cycle result", () -> {
                TestCenterState.cycle(selected);
                rebuildWidgets();
            });
            addActionSlot(1, y, "Reset", () -> {
                TestCenterState.reset();
                rebuildWidgets();
            });
            addActionSlot(2, y, "Export", () -> exportTests(snapshot));
        } else if (tab == Tab.SYNC) {
            addActionSlot(0, y, "Resync", () -> PsNetworking.requestClientResync(
                    "manual sync doctor request"));
            addActionSlot(1, y, "Retry", SafeRetryTray::retry);
        } else if (tab == Tab.GUIDE) {
            addActionSlot(0, y, "Palette", () -> Minecraft.getInstance().setScreen(
                    new CommandPaletteScreen()));
            addActionSlot(1, y, "HUD editor", () -> Minecraft.getInstance().setScreen(
                    new HudEditorScreen()));
            addActionSlot(2, y, "Compact", () -> {
                ClientPreferences.toggleCompactLayout();
                rebuildWidgets();
            });
            addActionSlot(3, y, "Text size", () -> {
                ClientPreferences.cycleTextScale();
                rebuildWidgets();
            });
        } else if (tab == Tab.STUDIO) {
            addActionSlot(0, y, "Open Studio", () -> Minecraft.getInstance().setScreen(new StudioScreen()));
        }
    }

    private void addActionSlot(int index, int y, String label, Runnable operation) {
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(width, height);
        int column = Math.floorMod(index, 2);
        int row = index / 2;
        int buttonWidth = Math.max(48, (detailWidth(frame) - 9) / 2);
        addAction(detailLeft(frame) + 3 + column * (buttonWidth + 2),
                frame.contentBottom() - 21 - row * 19, label, operation, buttonWidth);
    }

    private void addAction(int x, int y, String label, Runnable operation, int buttonWidth) {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> operation.run())
                .bounds(x, y, buttonWidth, 18).build());
    }

    private void renderSkillDashboard(
            GuiGraphics graphics,
            AdvancementUi.Frame frame,
            ClientNetworkState.Snapshot snapshot,
            int mouseX,
            int mouseY
    ) {
        ProgressionWorkbenchLayout.Layout layout = ProgressionWorkbenchLayout.calculate(frame);
        renderTiledBackground(graphics, layout.content(), theme.workbenchBackground());
        AdvancementUi.renderInset(graphics, layout.dossier().left(), layout.dossier().top(),
                layout.dossier().right(), layout.dossier().bottom());
        AdvancementUi.renderInset(graphics, layout.selector().left(), layout.selector().top(),
                layout.selector().right(), layout.selector().bottom());
        AdvancementUi.renderInset(graphics, layout.header().left(), layout.header().top(),
                layout.header().right(), layout.header().bottom());
        AdvancementUi.renderInset(graphics, layout.graph().left(), layout.graph().top(),
                layout.graph().right(), layout.graph().bottom());
        AdvancementUi.renderInset(graphics, layout.details().left(), layout.details().top(),
                layout.details().right(), layout.details().bottom());
        skillNodeCenters = Map.of();
        renderedTreeId = null;
        renderedSkillIndex = -1;
        if (rows.isEmpty()) {
            renderSkillDossier(graphics, layout, snapshot, Optional.empty(), mouseX, mouseY);
            graphics.drawCenteredString(font,
                    Component.translatable("screen.progressiveskills.skills.none"),
                    layout.graph().centerX(), layout.graph().centerY(), 0xFFAAAAAA);
            return;
        }
        int preview = previewEntryIndex();
        renderedSkillIndex = preview;
        Row row = rows.get(preview);
        ResourceLocation skillId = row.id().orElseThrow();
        Optional<SkillTreeModel> tree = boundTree(snapshot, skillId);
        renderSkillDossier(graphics, layout, snapshot, tree, mouseX, mouseY);
        renderSkillHeader(graphics, layout.header(), snapshot, row, skillId);
        if (tree.isEmpty() || snapshot.visibleState().isEmpty()) {
            renderSkillWithoutTree(graphics, layout, row, snapshot.visibleState().isPresent());
            return;
        }
        renderBoundSkillTree(
                graphics,
                layout,
                snapshot,
                tree.orElseThrow(),
                snapshot.visibleState().orElseThrow(),
                mouseX,
                mouseY
        );
    }

    private void renderSkillDossier(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Layout layout,
            ClientNetworkState.Snapshot snapshot,
            Optional<SkillTreeModel> selectedTree,
            int mouseX,
            int mouseY
    ) {
        ProgressionWorkbenchLayout.Rect meter = layout.meter();
        long totalLevel = rows.stream().flatMap(row -> row.id().stream())
                .mapToLong(id -> skillLevel(snapshot, id)).sum();
        int textX = meter.left() + 2;
        int textWidth = Math.max(8, meter.width() - 4);
        Component total = Component.translatable(
                "screen.progressiveskills.skills.total_level_value", totalLevel);
        graphics.drawString(font, font.plainSubstrByWidth(total.getString(), textWidth),
                textX, meter.top() + 1, 0xFFFFD65C, false);
        Component tracks = Component.translatable("screen.progressiveskills.skills.tracks", rows.size());
        graphics.drawString(font, font.plainSubstrByWidth(tracks.getString(), textWidth),
                textX, meter.top() + 12, 0xFFFFFFFF, false);

        ProgressionWorkbenchLayout.Rect balances = layout.balances();
        graphics.drawString(font,
                Component.translatable("screen.progressiveskills.skills.unspent_points"),
                balances.left() + 1, balances.top(), theme.workbenchColor("point_text"), false);
        List<PointBalance> points = pointBalances(snapshot, selectedTree);
        int pointY = balances.top() + 12;
        if (points.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("screen.progressiveskills.skills.no_points"),
                    balances.left() + 1, pointY, 0xFF888888, false);
        } else {
            for (PointBalance point : points) {
                if (pointY + 16 > balances.bottom()) {
                    break;
                }
                graphics.renderItem(point.icon(), balances.left() + 1, pointY - 3);
                String value = point.display().getString() + "  " + point.balance();
                graphics.drawString(font, font.plainSubstrByWidth(
                                value, Math.max(8, balances.width() - 21)),
                        balances.left() + 20, pointY + 1,
                        theme.workbenchColor("point_text"), false);
                pointY += 17;
            }
        }

        ProgressionWorkbenchLayout.Rect player = layout.player();
        if (minecraft != null && minecraft.player != null && player.height() >= 24) {
            int scale = Math.clamp(Math.min(player.width(), player.height()) / 3, 14, 34);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    player.left(),
                    player.top(),
                    player.right(),
                    player.bottom(),
                    scale,
                    0.0F,
                    mouseX,
                    mouseY,
                    minecraft.player
            );
        } else {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.progressiveskills.skills.player_unavailable"),
                    player.centerX(), player.centerY(), 0xFF888888);
        }
    }

    private void renderSkillHeader(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Rect header,
            ClientNetworkState.Snapshot snapshot,
            Row row,
            ResourceLocation skillId
    ) {
        int iconX = header.left() + 5;
        int iconY = header.top() + Math.max(2, (header.height() - 16) / 2);
        graphics.renderItem(rowIcon(snapshot, row), iconX, iconY);
        int textX = iconX + 21;
        int progressLeft = Math.max(textX + 82, header.left() + header.width() * 56 / 100);
        int availableWidth = Math.max(20, progressLeft - textX - 5);
        String name = font.plainSubstrByWidth(UiText.legacy(row.label()).getString(), availableWidth);
        graphics.drawString(font, name, textX, header.top() + 4, 0xFFFFFFFF, false);
        String subtitle = Component.translatable(
                "screen.progressiveskills.skills.level", skillLevel(snapshot, skillId)).getString();
        graphics.drawString(font, font.plainSubstrByWidth(subtitle, availableWidth),
                textX, header.top() + 14, 0xFFB8B8B8, false);
        long into = skillProgressBalance(snapshot, SkillStateIds.intoLevelXp(skillId));
        long next = skillProgressBalance(snapshot, SkillStateIds.nextLevelXp(skillId));
        int progressRight = header.right() - 5;
        int progressWidth = Math.max(12, progressRight - progressLeft);
        Component progress = snapshot.visibleState().isEmpty()
                ? Component.translatable("screen.progressiveskills.skills.progress_unavailable")
                : next <= 0L
                ? Component.translatable("screen.progressiveskills.skills.max_level")
                : Component.translatable(
                "screen.progressiveskills.skills.next_level",
                FixedPoint.format(into),
                FixedPoint.format(next)
        );
        graphics.drawString(
                font,
                font.plainSubstrByWidth(progress.getString(), progressWidth),
                progressLeft,
                header.top() + 3,
                snapshot.visibleState().isPresent() && next <= 0L ? 0xFFFFD65C : 0xFFFFFFFF,
                false
        );
        int barTop = header.bottom() - 8;
        graphics.fill(progressLeft, barTop, progressRight, barTop + 5, 0xD0181818);
        if (next > 0L && into > 0L) {
            double ratio = Math.clamp(into / (double) next, 0.0D, 1.0D);
            graphics.fill(
                    progressLeft,
                    barTop,
                    progressLeft + (int) Math.round(progressWidth * ratio),
                    barTop + 5,
                    theme.workbenchColor("meter_fill")
            );
        }
    }

    private void renderSkillWithoutTree(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Layout layout,
            Row row,
            boolean hasPlayerState
    ) {
        Component message = hasPlayerState
                ? Component.translatable("screen.progressiveskills.skills.no_bound_tree")
                : Component.translatable("screen.progressiveskills.skills.state_unavailable");
        graphics.drawCenteredString(font, message,
                layout.graph().centerX(), layout.graph().centerY(), 0xFFAAAAAA);
        int x = layout.details().left() + 5;
        int y = layout.details().top() + 5;
        drawWrapped(graphics, Component.literal(row.detail()), x, y,
                Math.max(20, layout.details().width() - 10),
                layout.details().bottom() - 5, 0xFFB8B8B8);
    }

    private void renderBoundSkillTree(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Layout layout,
            ClientNetworkState.Snapshot snapshot,
            SkillTreeModel tree,
            VisiblePlayerState state,
            int mouseX,
            int mouseY
    ) {
        renderedTreeId = tree.id();
        TreeViewport viewport = treeViewports.computeIfAbsent(tree.id(), ignored -> new TreeViewport());
        if (!viewport.initialized) {
            centerTreeViewport(viewport, tree.tree(), layout.graph());
        } else {
            clampTreeViewport(viewport, tree.tree(), layout.graph());
        }
        var centers = new LinkedHashMap<ResourceLocation, ProgressionWorkbenchLayout.Point>();
        for (DefinitionProjection.NodeView node : tree.tree().nodes()) {
            centers.put(node.id(), treeNodePoint(viewport, node, layout.graph()));
        }
        skillNodeCenters = Map.copyOf(centers);
        graphics.enableScissor(layout.graph().left() + 1, layout.graph().top() + 1,
                layout.graph().right() - 1, layout.graph().bottom() - 1);
        for (DefinitionProjection.NodeView node : tree.tree().nodes()) {
            ProgressionWorkbenchLayout.Point target = skillNodeCenters.get(node.id());
            SkillNodeStatus targetStatus = skillNodeStatus(tree.tree(), node, state);
            for (ResourceLocation requirement : node.requires()) {
                drawWorkbenchConnector(
                        graphics,
                        skillNodeCenters.get(requirement),
                        target,
                        targetStatus == SkillNodeStatus.OWNED
                                ? theme.workbenchColor("owned_path")
                                : theme.workbenchColor("required_path")
                );
            }
            for (ResourceLocation requirement : node.requiresAny()) {
                drawWorkbenchConnector(
                        graphics,
                        skillNodeCenters.get(requirement),
                        target,
                        targetStatus == SkillNodeStatus.OWNED
                                ? theme.workbenchColor("owned_path")
                                : theme.workbenchColor("alternative_path")
                );
            }
        }
        Optional<DefinitionProjection.NodeView> hovered = hoveredSkillNode(tree.tree(), mouseX, mouseY);
        DefinitionProjection.NodeView inspected = displayedSkillNode(tree.tree(), hovered);
        for (DefinitionProjection.NodeView node : tree.tree().nodes()) {
            ProgressionWorkbenchLayout.Point point = skillNodeCenters.get(node.id());
            SkillNodeStatus status = skillNodeStatus(tree.tree(), node, state);
            boolean selectedNode = node.id().equals(inspected.id());
            boolean hoveredNode = hovered.stream().anyMatch(value -> value.id().equals(node.id()));
            boolean major = node.requires().isEmpty() && node.requiresAny().isEmpty();
            if (major) {
                drawDiamondOutline(graphics, point.x(), point.y(), 17,
                        theme.workbenchColor("selected_node"));
            }
            int stateColor = theme.workbenchColor(status.colorKey);
            graphics.fill(point.x() - 15, point.y() - 15, point.x() + 15, point.y() + 15,
                    withAlpha(stateColor, 0x70));
            if (selectedNode || hoveredNode) {
                int outline = hoveredNode
                        ? theme.workbenchColor("hovered_node")
                        : theme.workbenchColor("selected_node");
                drawRectOutline(graphics, point.x() - 16, point.y() - 16,
                        point.x() + 16, point.y() + 16, outline);
            }
            AdvancementUi.renderNodeFrame(
                    graphics, point.x(), point.y(), status.frame, false);
            graphics.renderItem(ProjectionPresentation.icon(node.icon(), Items.BARRIER),
                    point.x() - 8, point.y() - 8);
            Integer rank = state.nodeRanks().get(node.id());
            if (rank != null) {
                graphics.drawString(font, Integer.toString(rank),
                        point.x() + 7, point.y() + 6, 0xFFFFFFFF, true);
            }
        }
        graphics.disableScissor();
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.progressiveskills.skills.tree_controls",
                        Math.round(viewport.zoom * 100.0D)
                ),
                layout.graph().left() + 4,
                layout.graph().bottom() - font.lineHeight - 3,
                0xFFB8B8B8,
                false
        );
        renderSkillNodeDetails(graphics, layout.details(), snapshot, tree, inspected, state);
    }

    private void renderSkillNodeDetails(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Rect details,
            ClientNetworkState.Snapshot snapshot,
            SkillTreeModel tree,
            DefinitionProjection.NodeView node,
            VisiblePlayerState state
    ) {
        int contentBottom = details.bottom() - (details.height() >= 80 ? 24 : 4);
        graphics.enableScissor(details.left() + 1, details.top() + 1,
                details.right() - 1, contentBottom);
        int x = details.left() + 5;
        int y = details.top() + 5;
        int width = Math.max(20, details.width() - 10);
        graphics.renderItem(ProjectionPresentation.icon(node.icon(), Items.BARRIER), x, y);
        Component name = ProjectionPresentation.component(node.display());
        int nameX = x + 21;
        int nameWidth = Math.max(12, details.right() - nameX - 4);
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), nameWidth),
                nameX, y + 3, 0xFFFFFFFF, false);
        y += 20;
        SkillNodeStatus status = skillNodeStatus(tree.tree(), node, state);
        y = drawWrapped(graphics, status.label(), x, y, width,
                contentBottom, theme.workbenchColor(status.colorKey));
        ItemStack currencyIcon = currencyIcon(snapshot, tree.tree().currency());
        if (y + 16 < contentBottom) {
            graphics.renderItem(currencyIcon, x, y - 2);
            long balance = tree.tree().visibleCurrencyBalance(state.balances());
            Component cost = Component.translatable(
                    "screen.progressiveskills.skills.node_cost",
                    node.cost(),
                    displayName(snapshot, tree.tree().currency()),
                    balance
            );
            y = drawWrapped(graphics, cost, x + 20, y + 1,
                    Math.max(20, width - 20), contentBottom, 0xFFD0D0D0);
            y += 3;
        }
        if (!node.requiresAny().isEmpty()) {
            y = drawWrapped(graphics,
                    Component.translatable("screen.progressiveskills.skills.choice_path"),
                    x, y, width, contentBottom,
                    theme.workbenchColor("alternative_path"));
        } else if (!node.requires().isEmpty()) {
            y = drawWrapped(graphics,
                    Component.translatable("screen.progressiveskills.skills.required_path"),
                    x, y, width, contentBottom,
                    theme.workbenchColor("required_path"));
        }
        if (node.description().isPresent()) {
            y += 3;
            drawWrapped(graphics,
                    ProjectionPresentation.component(node.description().orElseThrow()),
                    x, y, width, contentBottom, 0xFFB8B8B8);
        }
        graphics.disableScissor();
    }

    private void renderSkillTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderedTreeId == null || !skillGraph().contains(mouseX, mouseY)) {
            return;
        }
        ClientNetworkState.Snapshot snapshot = PsNetworking.clientSnapshot();
        if (snapshot.visibleState().isEmpty()) {
            return;
        }
        treeById(snapshot, renderedTreeId).ifPresent(tree ->
                hoveredSkillNode(tree.tree(), mouseX, mouseY).ifPresent(node -> {
                    SkillNodeStatus status = skillNodeStatus(
                            tree.tree(),
                            node,
                            snapshot.visibleState().orElseThrow()
                    );
                    renderBoundedTooltip(
                            graphics,
                            skillTooltipLines(snapshot, tree, node, status),
                            mouseX,
                            mouseY
                    );
                }));
    }

    private List<Component> skillTooltipLines(
            ClientNetworkState.Snapshot snapshot,
            SkillTreeModel tree,
            DefinitionProjection.NodeView node,
            SkillNodeStatus status
    ) {
        String description = node.description().map(ProjectionPresentation::component)
                .map(Component::getString).orElse("");
        Map<String, String> replacements = Map.of(
                "{name}", ProjectionPresentation.component(node.display()).getString(),
                "{state}", status.label().getString(),
                "{cost}", Long.toString(node.cost()),
                "{currency}", displayName(snapshot, tree.tree().currency()).getString(),
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
            lines.add(FormattedCharSequence.forward(
                    "...",
                    net.minecraft.network.chat.Style.EMPTY
            ));
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

    private List<PointBalance> pointBalances(
            ClientNetworkState.Snapshot snapshot,
            Optional<SkillTreeModel> selectedTree
    ) {
        if (snapshot.visibleState().isEmpty() || snapshot.activeDefinitions().isEmpty()) {
            return List.of();
        }
        var currencies = new LinkedHashMap<ResourceLocation, DefinitionProjection.TreeView>();
        selectedTree.ifPresent(tree -> currencies.put(tree.tree().currency(), tree.tree()));
        snapshot.activeDefinitions().orElseThrow().definitions().entrySet().stream()
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.TREE))
                .flatMap(entry -> entry.getValue().tree().stream())
                .filter(tree -> tree.boundSkill().isPresent())
                .forEach(tree -> currencies.putIfAbsent(tree.currency(), tree));
        VisiblePlayerState state = snapshot.visibleState().orElseThrow();
        return currencies.entrySet().stream().limit(theme.pointBalanceLimit())
                .map(entry -> new PointBalance(
                        entry.getKey(),
                        displayName(snapshot, entry.getKey()),
                        currencyIcon(snapshot, entry.getKey()),
                        entry.getValue().visibleCurrencyBalance(state.balances())
                ))
                .toList();
    }

    private static Optional<SkillTreeModel> boundTree(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation skillId
    ) {
        return snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.TREE))
                .filter(entry -> entry.getValue().tree().stream()
                        .flatMap(tree -> tree.boundSkill().stream())
                        .anyMatch(skillId::equals))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SkillTreeModel(
                        entry.getKey().id(),
                        ProjectionPresentation.display(entry.getValue(), UiText.prettyId(entry.getKey().id())),
                        entry.getValue(),
                        entry.getValue().tree().orElseThrow()
                ))
                .findFirst();
    }

    private static Optional<SkillTreeModel> treeById(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation treeId
    ) {
        return snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.TREE))
                .filter(entry -> entry.getKey().id().equals(treeId))
                .filter(entry -> entry.getValue().tree().isPresent())
                .map(entry -> new SkillTreeModel(
                        entry.getKey().id(),
                        ProjectionPresentation.display(entry.getValue(), UiText.prettyId(entry.getKey().id())),
                        entry.getValue(),
                        entry.getValue().tree().orElseThrow()
                ))
                .findFirst();
    }

    private Optional<DefinitionProjection.NodeView> selectedSkillNode(
            DefinitionProjection.TreeView tree
    ) {
        if (tree.nodes().isEmpty()) {
            return Optional.empty();
        }
        Optional<DefinitionProjection.NodeView> selected = inspectedSkillNode == null
                ? Optional.empty()
                : tree.nodes().stream().filter(node -> node.id().equals(inspectedSkillNode)).findFirst();
        return selected.or(() -> tree.nodes().stream().filter(node ->
                        node.requires().isEmpty() && node.requiresAny().isEmpty()).findFirst())
                .or(() -> Optional.of(tree.nodes().getFirst()));
    }

    private static Optional<NetworkPayloads.TreeRefundPreview> matchingTreeRefundPreview(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation treeId,
            ResourceLocation nodeId
    ) {
        if (snapshot.visibleState().isEmpty()) {
            return Optional.empty();
        }
        return snapshot.treeRefundPreview().filter(preview ->
                preview.treeId().equals(treeId)
                        && preview.nodeId().equals(nodeId)
                        && preview.stateRevision()
                        == snapshot.visibleState().orElseThrow().stateRevision()
        );
    }

    private ProgressionWorkbenchLayout.Rect skillGraph() {
        return ProgressionWorkbenchLayout.calculate(
                AdvancementUi.largeFrame(width, height)
        ).graph();
    }

    private static ProgressionWorkbenchLayout.Point treeNodePoint(
            TreeViewport viewport,
            DefinitionProjection.NodeView node,
            ProgressionWorkbenchLayout.Rect graph
    ) {
        return new ProgressionWorkbenchLayout.Point(
                graph.centerX() + (int) Math.round(
                        (node.column() * TREE_COLUMN_SPACING + viewport.panX) * viewport.zoom
                ),
                graph.centerY() + (int) Math.round(
                        (node.row() * TREE_ROW_SPACING + viewport.panY) * viewport.zoom
                )
        );
    }

    private static void centerTreeViewport(
            TreeViewport viewport,
            DefinitionProjection.TreeView tree,
            ProgressionWorkbenchLayout.Rect graph
    ) {
        int minimumColumn = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maximumColumn = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minimumRow = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maximumRow = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        viewport.panX = -(minimumColumn + maximumColumn) * TREE_COLUMN_SPACING / 2.0D;
        viewport.panY = -(minimumRow + maximumRow) * TREE_ROW_SPACING / 2.0D;
        double horizontal = (maximumColumn - minimumColumn) * (double) TREE_COLUMN_SPACING + 38.0D;
        double vertical = (maximumRow - minimumRow) * (double) TREE_ROW_SPACING + 38.0D;
        double horizontalFit = graph.width() / Math.max(1.0D, horizontal);
        double verticalFit = graph.height() / Math.max(1.0D, vertical);
        viewport.zoom = Math.clamp(
                Math.min(horizontalFit, verticalFit),
                MIN_TREE_ZOOM,
                MAX_TREE_ZOOM
        );
        viewport.initialized = true;
        clampTreeViewport(viewport, tree, graph);
    }

    private static void clampTreeViewport(
            TreeViewport viewport,
            DefinitionProjection.TreeView tree,
            ProgressionWorkbenchLayout.Rect graph
    ) {
        int minimumColumn = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        int maximumColumn = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::column).max().orElse(0);
        int minimumRow = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int maximumRow = tree.nodes().stream()
                .mapToInt(DefinitionProjection.NodeView::row).max().orElse(0);
        double horizontal = graph.width() / (2.0D * viewport.zoom) - 18.0D;
        double vertical = graph.height() / (2.0D * viewport.zoom) - 18.0D;
        viewport.panX = Math.clamp(
                viewport.panX,
                -maximumColumn * TREE_COLUMN_SPACING - horizontal,
                -minimumColumn * TREE_COLUMN_SPACING + horizontal
        );
        viewport.panY = Math.clamp(
                viewport.panY,
                -maximumRow * TREE_ROW_SPACING - vertical,
                -minimumRow * TREE_ROW_SPACING + vertical
        );
    }

    private static ItemStack currencyIcon(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation currencyId
    ) {
        return definitionEntry(snapshot, DefinitionKinds.CURRENCY, currencyId)
                .map(entry -> ProjectionPresentation.icon(entry, Items.EMERALD))
                .orElseGet(() -> new ItemStack(Items.EMERALD));
    }

    private static Optional<DefinitionProjection.Entry> definitionEntry(
            ClientNetworkState.Snapshot snapshot,
            com.envisione.progressiveskills.common.id.DefinitionKind kind,
            ResourceLocation id
    ) {
        return snapshot.activeDefinitions().stream()
                .flatMap(projection -> projection.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(kind) && entry.getKey().id().equals(id))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private Optional<DefinitionProjection.NodeView> hoveredSkillNode(
            DefinitionProjection.TreeView tree,
            int mouseX,
            int mouseY
    ) {
        return tree.nodes().stream().filter(node -> {
            ProgressionWorkbenchLayout.Point point = skillNodeCenters.get(node.id());
            if (point == null) {
                return false;
            }
            int x = mouseX - point.x();
            int y = mouseY - point.y();
            return x * x + y * y <= 15 * 15;
        }).findFirst();
    }

    private DefinitionProjection.NodeView displayedSkillNode(
            DefinitionProjection.TreeView tree,
            Optional<DefinitionProjection.NodeView> hovered
    ) {
        DefinitionProjection.NodeView fallback = tree.nodes().stream()
                .filter(node -> node.requires().isEmpty() && node.requiresAny().isEmpty())
                .min(Comparator.comparingInt(DefinitionProjection.NodeView::row)
                        .thenComparingInt(DefinitionProjection.NodeView::column)
                        .thenComparing(DefinitionProjection.NodeView::id, ResourceLocation::compareNamespaced))
                .orElse(tree.nodes().getFirst());
        List<ResourceLocation> available = tree.nodes().stream()
                .map(DefinitionProjection.NodeView::id).toList();
        ResourceLocation chosen = ProgressionWorkbenchLayout.inspectedValue(
                hovered.map(DefinitionProjection.NodeView::id),
                inspectedSkillNode,
                fallback.id(),
                available
        );
        return tree.nodes().stream().filter(node -> node.id().equals(chosen))
                .findFirst().orElse(fallback);
    }

    private static SkillNodeStatus skillNodeStatus(
            DefinitionProjection.TreeView tree,
            DefinitionProjection.NodeView node,
            VisiblePlayerState state
    ) {
        if (state.nodeRanks().containsKey(node.id())) {
            return SkillNodeStatus.OWNED;
        }
        if (!tree.enabled() || state.quarantined()) {
            return SkillNodeStatus.SUSPENDED;
        }
        boolean required = node.requires().stream().allMatch(state.nodeRanks()::containsKey);
        boolean any = node.requiresAny().isEmpty()
                || node.requiresAny().stream().anyMatch(state.nodeRanks()::containsKey);
        boolean levels = node.minimumSkillLevels().entrySet().stream().allMatch(entry ->
                state.balances().getOrDefault(SkillStateIds.level(entry.getKey()).toString(), 0L)
                        >= entry.getValue());
        long balance = tree.visibleCurrencyBalance(state.balances());
        boolean currency;
        try {
            currency = Math.subtractExact(balance, node.cost()) >= tree.currencyMinimum();
        } catch (ArithmeticException exception) {
            currency = false;
        }
        return required && any && levels && currency
                ? SkillNodeStatus.AVAILABLE : SkillNodeStatus.LOCKED;
    }

    private int drawWrapped(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int bottom,
            int color
    ) {
        for (var line : font.split(text, Math.max(8, width))) {
            if (y + font.lineHeight > bottom) {
                break;
            }
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    private static void drawWorkbenchConnector(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Point source,
            ProgressionWorkbenchLayout.Point target,
            int color
    ) {
        if (source == null || target == null) {
            return;
        }
        int middleY = (source.y() + target.y()) / 2;
        graphics.vLine(source.x(), Math.min(source.y(), middleY), Math.max(source.y(), middleY), color);
        graphics.hLine(Math.min(source.x(), target.x()), Math.max(source.x(), target.x()), middleY, color);
        graphics.vLine(target.x(), Math.min(middleY, target.y()), Math.max(middleY, target.y()), color);
    }

    private static void drawDiamondOutline(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int color
    ) {
        for (int offset = 0; offset <= radius; offset++) {
            plot(graphics, centerX - offset, centerY - radius + offset, color);
            plot(graphics, centerX + offset, centerY - radius + offset, color);
            plot(graphics, centerX - offset, centerY + radius - offset, color);
            plot(graphics, centerX + offset, centerY + radius - offset, color);
        }
    }

    private static void drawRectOutline(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        graphics.hLine(left, right, top, color);
        graphics.hLine(left, right, bottom, color);
        graphics.vLine(left, top, bottom, color);
        graphics.vLine(right, top, bottom, color);
    }

    private static void plot(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | alpha << 24;
    }

    private static void renderTiledBackground(
            GuiGraphics graphics,
            ProgressionWorkbenchLayout.Rect area,
            ResourceLocation texture
    ) {
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        for (int x = area.left(); x < area.right(); x += 16) {
            for (int y = area.top(); y < area.bottom(); y += 16) {
                graphics.blit(
                        texture,
                        x,
                        y,
                        0.0F,
                        0.0F,
                        Math.min(16, area.right() - x),
                        Math.min(16, area.bottom() - y),
                        16,
                        16
                );
            }
        }
        graphics.disableScissor();
    }

    private void renderClassDashboard(
            GuiGraphics graphics,
            AdvancementUi.Frame frame,
            ClientNetworkState.Snapshot snapshot
    ) {
        int slotWidth = classSlotWidth(frame);
        int slotsRight = frame.contentX() + slotWidth;
        int detailsLeft = detailLeft(frame);
        AdvancementUi.renderInset(graphics, frame.contentX() + 2, frame.contentY() + 2,
                slotsRight, frame.contentBottom() - 2);
        AdvancementUi.renderInset(graphics, slotsRight + 3, frame.contentY() + 2,
                detailsLeft - 3, frame.contentBottom() - 2);
        AdvancementUi.renderInset(graphics, detailsLeft, frame.contentY() + 2,
                frame.contentRight() - 2, frame.contentBottom() - 2);
        ClassSlotModel slot = selectedClassSlotModel().orElse(null);
        if (slot != null) {
            int used = usedClassCapacity(snapshot, slot.id());
            Component heading = Component.empty().append(slot.display()).append(". ")
                    .append(Component.translatable("screen.progressiveskills.classes.capacity",
                            used, slot.view().capacity()));
            graphics.drawCenteredString(font, heading,
                    slotsRight + 3 + (detailsLeft - slotsRight - 6) / 2,
                    frame.contentY() + 9, 0xFFFFD65C);
        }
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No classes are available for this slot."),
                    slotsRight + 3 + (detailsLeft - slotsRight - 6) / 2,
                    frame.contentY() + 45, 0xFFAAAAAA);
            return;
        }
        graphics.enableScissor(detailsLeft + 1, frame.contentY() + 3,
                frame.contentRight() - 3, frame.contentBottom() - 23);
        int preview = previewEntryIndex();
        Row row = rows.get(preview);
        ResourceLocation id = row.id().orElseThrow();
        DefinitionProjection.ClassView view = classView(snapshot, id).orElse(null);
        int x = detailsLeft + 5;
        int y = frame.contentY() + 7;
        int maxWidth = Math.max(40, frame.contentRight() - x - 5);
        graphics.renderItem(rowIcon(snapshot, row), x, y);
        for (var line : font.split(UiText.legacy(row.label()), Math.max(20, maxWidth - 21))) {
            graphics.drawString(font, line, x + 21, y + 3, 0xFFFFFFFF, false);
            y += font.lineHeight + 1;
        }
        y = Math.max(y + 4, frame.contentY() + 29);
        VisiblePlayerState.ClassSelection selection = snapshot.visibleState().stream()
                .map(state -> state.selectedClasses().get(id)).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        Component state = selection == null
                ? Component.translatable("screen.progressiveskills.classes.not_selected")
                : Component.translatable("screen.progressiveskills.classes.selected", selection.activity());
        graphics.drawString(font, state, x, y, selection == null ? 0xFFB8B8B8 : 0xFF7CFC98, false);
        if (view == null) {
            graphics.disableScissor();
            return;
        }
        y += 12;
        graphics.drawString(font, Component.translatable(
                        "screen.progressiveskills.classes.weight", view.slotCost()),
                x, y, 0xFFFFD65C, false);
        y += 11;
        Component cost = view.selectionCost().<Component>map(value -> Component.translatable(
                        "screen.progressiveskills.classes.cost", value.amount(),
                        displayName(snapshot, value.currency())))
                .orElseGet(() -> Component.translatable("screen.progressiveskills.classes.free"));
        for (var line : font.split(cost, maxWidth)) {
            graphics.drawString(font, line, x, y, 0xFF80C8FF, false);
            y += font.lineHeight + 1;
        }
        Component requirements = Component.translatable("screen.progressiveskills.classes.requirements",
                view.minimumSkillLevels().size(), view.requiredNodes().size(), view.requiredClasses().size());
        for (var line : font.split(requirements, maxWidth)) {
            graphics.drawString(font, line, x, y, 0xFFB8B8B8, false);
            y += font.lineHeight + 1;
        }
        graphics.drawString(font, Component.translatable(
                        "screen.progressiveskills.classes.grants", view.grants().size()),
                x, y, 0xFFB8B8B8, false);
        graphics.disableScissor();
    }

    private void renderDetails(GuiGraphics graphics, ClientNetworkState.Snapshot snapshot) {
        float scale = ClientPreferences.textScale() / 100.0F;
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(width, height);
        int x = Math.round((detailLeft(frame) + 6) / scale);
        int y = Math.round((frame.contentY() + 6) / scale);
        int maxWidth = Math.max(40, Math.round((detailWidth(frame) - 12) / scale));
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.literal("No entries are available."), x, y, 0xAAAAAA, false);
            graphics.pose().popPose();
            return;
        }
        int detailIndex = cards.stream().filter(ProgressionCardButton::isHovered)
                .mapToInt(ProgressionCardButton::rowIndex).findFirst().orElse(selected);
        for (var line : font.split(Component.literal(rows.get(detailIndex).detail()), maxWidth)) {
            graphics.drawString(font, line, x, y, 0xE0E0E0, false);
            y += font.lineHeight + 2;
        }
        if (tab == Tab.COMPARE) {
            y += 4;
            for (var line : font.split(Component.literal("Plan one. "
                    + planSummary(snapshot, compareFirst)), maxWidth)) {
                graphics.drawString(font, line, x, y, 0x7CFC98, false);
                y += font.lineHeight + 2;
            }
            for (var line : font.split(Component.literal("Plan two. "
                    + planSummary(snapshot, compareSecond)), maxWidth)) {
                graphics.drawString(font, line, x, y, 0x88C8FF, false);
                y += font.lineHeight + 2;
            }
        }
        if (tab == Tab.TESTS) {
            y += 4;
            graphics.drawString(font, Component.literal("Checkpoint. " + checkpoint(snapshot)),
                    x, y, 0x88C8FF, false);
        }
        if (tab == Tab.SYNC) {
            y += 4;
            String retry = SafeRetryTray.label().orElse("No retryable action.");
            graphics.drawString(font, Component.literal(retry), x, y, 0xFFCC66, false);
        }
        if (tab == Tab.CLASSES) {
            Optional<NetworkPayloads.ClassChangePreview> classPreview = selectedRow().flatMap(Row::id)
                    .flatMap(id -> matchingClassPreview(snapshot, id));
            if (classPreview.isPresent()) {
                NetworkPayloads.ClassChangePreview preview = classPreview.orElseThrow();
                int previewY = y + 6;
                String text = preview.allowed()
                        ? "Respec preview. Affected " + preview.affectedClasses()
                        + ". Balances after cost " + preview.costBalances() + "."
                        : "Respec blocked. " + String.join(". ", preview.blockers()) + ".";
                graphics.drawString(font, Component.literal(text), x, previewY,
                        preview.allowed() ? 0xFFCC66 : 0xFF8888, false);
            }
        }
        if (tab == Tab.CLAIMS) {
            if (snapshot.carrierMigrationPreview().isPresent()) {
                NetworkPayloads.CarrierMigrationPreview preview = snapshot.carrierMigrationPreview().orElseThrow();
                int previewY = y + 6;
                graphics.drawString(font, Component.literal(preview.message()), x, previewY,
                        preview.allowed() ? 0xFFCC66 : 0xFF8888, false);
            }
        }
        snapshot.lastIntentResult().ifPresent(result -> {
            int resultY = Math.round((frame.contentBottom() - 12) / scale);
            graphics.drawString(font, Component.literal(result.status() + ". " + result.message()),
                    x, resultY, result.status().name().equals("ACCEPTED") ? 0x7CFC98 : 0xFF8888, false);
        });
        graphics.pose().popPose();
    }

    private List<Row> rows(ClientNetworkState.Snapshot snapshot) {
        if (tab == Tab.TESTS) {
            var result = new ArrayList<Row>();
            for (int index = 0; index < TestCenterState.CHECKS.size(); index++) {
                result.add(new Row(Optional.empty(), Optional.empty(),
                        TestCenterState.result(index) + ". Check " + (index + 1), TestCenterState.CHECKS.get(index)));
            }
            return result;
        }
        if (tab == Tab.SYNC) {
            String detail = "Phase " + snapshot.phase() + ". Definitions " + snapshot.definitionCount()
                    + ". Cached sets " + snapshot.cachedDefinitionSets() + ". "
                    + snapshot.visibleState().map(state -> "Generation " + state.definitionRevision().generation()
                    + ". State revision " + state.stateRevision() + ". Sync revision " + state.syncRevision()
                    + ". Digest " + shortDigest(state.definitionRevision().semanticDigest()) + ".")
                    .orElse("No authoritative player state.")
                    + " Resync reason " + snapshot.lastResyncReason().orElse("none") + ".";
            return List.of(new Row(Optional.empty(), Optional.empty(), "Connection", detail));
        }
        if (tab == Tab.CLAIMS) {
            var result = new ArrayList<Row>();
            snapshot.visibleState().ifPresent(state -> {
                state.carriers().held().ifPresent(held -> result.add(new Row(
                        Optional.of(held.definitionId()), Optional.empty(), "Held " + held.definitionId().getPath(),
                        "Kind " + held.kind() + ". Charges " + held.charges() + ". Version "
                                + held.behaviorVersion() + ". Status " + held.status() + ". " + held.message())));
                state.carriers().pendingClaims().forEach(claim -> result.add(new Row(
                        Optional.of(claim.definitionId()), Optional.of(claim.claimId()),
                        "Claim " + claim.definitionId().getPath(), "Kind " + claim.kind() + ". Charges "
                                + claim.charges() + ". Version " + claim.behaviorVersion() + ". " + claim.reason())));
            });
            return List.copyOf(result);
        }
        if (tab == Tab.COMPARE) {
            return List.of(new Row(Optional.empty(), Optional.empty(), "Current build",
                    snapshot.visibleState().map(ProgressionScreen::buildSummary).orElse("State unavailable.")));
        }
        if (tab == Tab.STUDIO) {
            return List.of(new Row(Optional.empty(), Optional.empty(), "Authoring Studio",
                    "Create bounded drafts, edit TOML or JSON, lint, review diffs and history, resolve conflicts, publish, roll back, import, export, record fixtures, and export graphs."));
        }
        if (snapshot.activeDefinitions().isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Row>();
        for (Map.Entry<DefinitionKey, DefinitionProjection.Entry> value
                : snapshot.activeDefinitions().orElseThrow().definitions().entrySet()) {
            if (!matches(value.getKey())) {
                continue;
            }
            if (tab == Tab.CLASSES && selectedClassSlot != null
                    && value.getValue().classDefinition().stream()
                    .noneMatch(classView -> classView.slotId().equals(selectedClassSlot))) {
                continue;
            }
            String display = value.getValue().display().map(DefinitionProjection.Text::fallback)
                    .orElse(value.getKey().id().getPath());
            String description = value.getValue().description().map(DefinitionProjection.Text::fallback)
                    .orElse("No description.");
            result.add(new Row(Optional.of(value.getKey().id()), Optional.empty(), display,
                    rowDetail(snapshot, value.getKey(), value.getValue(), description)));
        }
        result.sort(Comparator.comparing(Row::label, String.CASE_INSENSITIVE_ORDER));
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (searchableTab() && !normalized.isEmpty()) {
            result.removeIf(row -> !row.label().toLowerCase(Locale.ROOT).contains(normalized)
                    && !row.detail().toLowerCase(Locale.ROOT).contains(normalized)
                    && row.id().map(ResourceLocation::toString).stream()
                    .noneMatch(value -> value.toLowerCase(Locale.ROOT).contains(normalized)));
        }
        return List.copyOf(result);
    }

    private boolean searchableTab() {
        return tab == Tab.TREES || tab == Tab.ABILITIES || tab == Tab.GUIDE;
    }

    private boolean matches(DefinitionKey key) {
        return switch (tab) {
            case SKILLS -> key.kind().equals(DefinitionKinds.SKILL);
            case TREES -> key.kind().equals(DefinitionKinds.TREE);
            case CLASSES -> key.kind().equals(DefinitionKinds.CLASS);
            case ABILITIES -> key.kind().equals(DefinitionKinds.ABILITY);
            case GUIDE -> true;
            default -> false;
        };
    }

    private void cycleCompare(boolean first, ClientNetworkState.Snapshot snapshot) {
        List<ResourceLocation> candidates = snapshot.activeDefinitions().stream().flatMap(value ->
                value.definitions().entrySet().stream().flatMap(entry -> {
                    if (entry.getKey().kind().equals(DefinitionKinds.CLASS)
                            || entry.getKey().kind().equals(DefinitionKinds.ABILITY)) {
                        return java.util.stream.Stream.of(entry.getKey().id());
                    }
                    if (entry.getKey().kind().equals(DefinitionKinds.TREE)) {
                        return entry.getValue().tree().stream()
                                .flatMap(tree -> tree.nodes().stream().map(DefinitionProjection.NodeView::id));
                    }
                    return java.util.stream.Stream.empty();
                })).distinct().sorted(ResourceLocation::compareNamespaced).toList();
        if (candidates.isEmpty()) {
            return;
        }
        ResourceLocation current = first ? compareFirst : compareSecond;
        int index = current == null ? -1 : candidates.indexOf(current);
        ResourceLocation next = candidates.get(Math.floorMod(index + 1, candidates.size()));
        if (first) {
            compareFirst = next;
        } else {
            compareSecond = next;
        }
        rebuildWidgets();
    }

    private void selectTab(Tab value) {
        tab = value;
        page = 0;
        selected = 0;
        query = "";
        rebuildWidgets();
    }

    private void changePage(int amount) {
        page += amount;
        selected = Math.min(rows.size() - 1,
                Math.max(0, page * visibleEntries(AdvancementUi.largeFrame(width, height))));
        rebuildWidgets();
    }

    private int visibleEntries(AdvancementUi.Frame frame) {
        if (tab == Tab.SKILLS) {
            return ProgressionWorkbenchLayout.calculate(frame).selectorCapacity();
        }
        if (tab == Tab.CLASSES) {
            int vertical = Math.max(1, (frame.contentBottom() - frame.contentY() - 32) / 41);
            return vertical * classColumns(frame);
        }
        int contentTop = frame.contentY() + (searchableTab() ? 27 : 5);
        int vertical = Math.max(1, (frame.contentBottom() - contentTop - 4) / 44);
        return vertical * cardColumns(frame);
    }

    static int classSlotWidth(AdvancementUi.Frame frame) {
        return Math.clamp(frame.contentWidth() / 5, 68, 104);
    }

    static int classColumns(AdvancementUi.Frame frame) {
        int gridWidth = detailLeft(frame) - frame.contentX() - classSlotWidth(frame) - 12;
        return Math.max(1, gridWidth / 41);
    }

    static int visibleClassSlots(AdvancementUi.Frame frame) {
        return Math.max(1, (frame.contentHeight() - 32) / 29);
    }

    private static int detailWidth(AdvancementUi.Frame frame) {
        return Math.clamp(frame.contentWidth() / 3, 106, 176);
    }

    private static int detailLeft(AdvancementUi.Frame frame) {
        return frame.contentRight() - detailWidth(frame) - 2;
    }

    private static int cardAreaWidth(AdvancementUi.Frame frame) {
        return detailLeft(frame) - frame.contentX();
    }

    private static int cardColumns(AdvancementUi.Frame frame) {
        return cardAreaWidth(frame) >= 288 ? 2 : 1;
    }

    private ItemStack rowIcon(ClientNetworkState.Snapshot snapshot, Row row) {
        ItemStack fallback = tabIcon(tab);
        return row.id().flatMap(id -> snapshot.activeDefinitions().stream()
                        .flatMap(value -> value.definitions().entrySet().stream())
                        .filter(entry -> entry.getKey().id().equals(id)
                                && (tab == Tab.GUIDE || tab == Tab.CLAIMS || matches(entry.getKey())))
                        .map(Map.Entry::getValue).findFirst())
                .map(entry -> ProjectionPresentation.icon(entry, fallback.getItem()))
                .orElse(fallback);
    }

    private static String cardSummary(Row row) {
        String detail = row.detail();
        for (String marker : List.of("Level ", "Owned nodes ", "Selected.", "Not selected.",
                "Owned.", "Not owned.", "Status ", "Charges ")) {
            int index = detail.indexOf(marker);
            if (index >= 0) {
                int end = detail.indexOf('.', index);
                return detail.substring(index, end < 0 ? detail.length() : end);
            }
        }
        int end = detail.indexOf('.');
        return end < 0 ? detail : detail.substring(0, end);
    }

    private int previewEntryIndex() {
        return selectors.stream().filter(ProgressionSelectorButton::isHoveredOrFocused)
                .mapToInt(ProgressionSelectorButton::entryIndex)
                .filter(index -> index >= 0 && index < rows.size())
                .findFirst().orElse(selected);
    }

    private static long skillLevel(ClientNetworkState.Snapshot snapshot, ResourceLocation id) {
        return snapshot.visibleState().map(state -> state.balances().getOrDefault(
                SkillStateIds.level(id).toString(), 0L)).orElse(0L);
    }

    private static long skillProgressBalance(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation balanceId
    ) {
        return snapshot.visibleState().map(state ->
                state.balances().getOrDefault(balanceId.toString(), 0L)).orElse(0L);
    }

    private static Optional<DefinitionProjection.ClassView> classView(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation classId
    ) {
        return snapshot.activeDefinitions().stream().flatMap(value -> value.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CLASS)
                        && entry.getKey().id().equals(classId))
                .map(Map.Entry::getValue).flatMap(entry -> entry.classDefinition().stream()).findFirst();
    }

    private static Component displayName(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation id
    ) {
        return snapshot.activeDefinitions().stream().flatMap(value -> value.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.CURRENCY)
                        && entry.getKey().id().equals(id))
                .map(Map.Entry::getValue)
                .map(entry -> ProjectionPresentation.display(entry, UiText.prettyId(id)))
                .findFirst().orElseGet(() -> Component.literal(UiText.prettyId(id)));
    }

    private void refreshClassSlots(ClientNetworkState.Snapshot snapshot) {
        var next = new ArrayList<ClassSlotModel>();
        snapshot.activeDefinitions().ifPresent(projection -> projection.definitions().forEach((key, entry) -> {
            if (key.kind().equals(DefinitionKinds.CLASS_SLOT) && entry.classSlot().isPresent()) {
                next.add(new ClassSlotModel(
                        key.id(),
                        ProjectionPresentation.display(entry, UiText.prettyId(key.id())),
                        entry,
                        entry.classSlot().orElseThrow()
                ));
            }
        }));
        next.sort(Comparator.comparing(ClassSlotModel::id, ResourceLocation::compareNamespaced));
        classSlots = List.copyOf(next);
        if (classSlots.isEmpty()) {
            selectedClassSlot = null;
            classSlotPage = 0;
            return;
        }
        if (selectedClassSlot == null || classSlots.stream().noneMatch(slot -> slot.id().equals(selectedClassSlot))) {
            selectedClassSlot = classSlots.getFirst().id();
            classSlotPage = 0;
        }
    }

    private Optional<ClassSlotModel> selectedClassSlotModel() {
        return classSlots.stream().filter(slot -> slot.id().equals(selectedClassSlot)).findFirst();
    }

    private static int usedClassCapacity(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation slotId
    ) {
        return snapshot.visibleState().stream().flatMap(state -> state.selectedClasses().values().stream())
                .filter(selection -> selection.slotId().stream().anyMatch(slotId::equals))
                .mapToInt(VisiblePlayerState.ClassSelection::slotCost).sum();
    }

    private void selectClassSlot(ResourceLocation slotId) {
        selectedClassSlot = slotId;
        page = 0;
        selected = 0;
        rebuildWidgets();
    }

    private void changeClassSlotPage(int amount) {
        classSlotPage += amount;
        rebuildWidgets();
    }

    private ItemStack tabIcon(Tab tab) {
        var fallback = switch (tab) {
            case MAIN -> Items.COMPASS;
            case SKILLS -> Items.EXPERIENCE_BOTTLE;
            case TREES -> Items.OAK_SAPLING;
            case CLASSES -> Items.ARMOR_STAND;
            case ABILITIES -> Items.BLAZE_POWDER;
            case CLAIMS -> Items.CHEST;
            case GUIDE -> Items.KNOWLEDGE_BOOK;
            case COMPARE -> Items.COMPASS;
            case TESTS -> Items.WRITABLE_BOOK;
            case SYNC -> Items.REDSTONE;
            case STUDIO -> Items.CRAFTING_TABLE;
        };
        return theme.tabIcon(tab.name().toLowerCase(Locale.ROOT), fallback);
    }

    private Optional<Row> selectedRow() {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(selected));
    }

    private static void safe(String label, java.util.function.BooleanSupplier sender) {
        if (!SafeRetryTray.sendOrRemember(label, sender)) {
            PsNetworking.requestClientResync("progression screen action rejected");
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("screen.progressiveskills.action_changed"), true);
            }
        }
    }

    private static Optional<Integer> firstFreeSlot(Optional<VisiblePlayerState> state) {
        if (state.isEmpty()) {
            return Optional.empty();
        }
        for (int slot = 0; slot < 8; slot++) {
            if (!state.orElseThrow().abilitySlots().containsKey(slot)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> assignedSlot(
            Optional<VisiblePlayerState> state,
            ResourceLocation abilityId
    ) {
        return state.stream().flatMap(value -> value.abilitySlots().entrySet().stream())
                .filter(entry -> entry.getValue().equals(abilityId)).map(Map.Entry::getKey).findFirst();
    }

    private static Optional<DefinitionProjection.AbilityView> abilityView(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation abilityId
    ) {
        return snapshot.activeDefinitions().stream().flatMap(value -> value.definitions().entrySet().stream())
                .filter(entry -> entry.getKey().kind().equals(DefinitionKinds.ABILITY)
                        && entry.getKey().id().equals(abilityId))
                .map(Map.Entry::getValue).flatMap(entry -> entry.ability().stream()).findFirst();
    }

    private static Optional<NetworkPayloads.ClassChangePreview> matchingClassPreview(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation classId
    ) {
        return snapshot.classChangePreview().filter(preview -> preview.classId().equals(classId)
                && preview.replacementClassId().isEmpty()
                && snapshot.visibleState().filter(state -> state.stateRevision() == preview.stateRevision()).isPresent());
    }

    private static String rowDetail(
            ClientNetworkState.Snapshot snapshot,
            DefinitionKey key,
            DefinitionProjection.Entry entry,
            String description
    ) {
        String prefix = key.kind().id().getPath() + ". " + key.id() + ". " + description;
        if (snapshot.visibleState().isEmpty()) {
            return prefix;
        }
        VisiblePlayerState state = snapshot.visibleState().orElseThrow();
        if (key.kind().equals(DefinitionKinds.SKILL)) {
            long level = state.balances().getOrDefault(SkillStateIds.level(key.id()).toString(), 0L);
            long active = state.balances().getOrDefault(SkillStateIds.activeXp(key.id()).toString(), 0L);
            long banked = state.balances().getOrDefault(SkillStateIds.bankedXp(key.id()).toString(), 0L);
            return prefix + " Level " + level + ". Active XP " + active + ". Banked XP " + banked + ".";
        }
        if (key.kind().equals(DefinitionKinds.CLASS)) {
            VisiblePlayerState.ClassSelection selected = state.selectedClasses().get(key.id());
            return prefix + (selected == null ? " Not selected." : " Selected. Status "
                    + selected.activity() + ". Slot cost " + selected.slotCost() + ".");
        }
        if (key.kind().equals(DefinitionKinds.ABILITY)) {
            VisiblePlayerState.AbilityState ability = state.abilities().get(key.id());
            Optional<Integer> slot = assignedSlot(Optional.of(state), key.id());
            return prefix + (ability == null ? " Not owned." : " Owned. Charges " + ability.charges()
                    + " of " + ability.maximumCharges() + ". Cooldown " + ability.cooldownRemainingTicks()
                    + ". Toggle " + ability.toggledOn() + ". Slot "
                    + slot.map(value -> Integer.toString(value + 1)).orElse("unassigned") + ".");
        }
        if (key.kind().equals(DefinitionKinds.TREE) && entry.tree().isPresent()) {
            long owned = entry.tree().orElseThrow().nodes().stream()
                    .filter(node -> state.nodeRanks().containsKey(node.id())).count();
            return prefix + " Owned nodes " + owned + " of " + entry.tree().orElseThrow().nodes().size() + ".";
        }
        return prefix;
    }

    private static String buildSummary(VisiblePlayerState state) {
        return "Classes " + state.selectedClasses().keySet() + ". Nodes " + state.nodeRanks().keySet()
                + ". Abilities " + state.abilitySlots() + ". Balances " + state.balances() + ".";
    }

    private static String planSummary(ClientNetworkState.Snapshot snapshot, ResourceLocation selected) {
        if (selected == null || snapshot.visibleState().isEmpty() || snapshot.activeDefinitions().isEmpty()) {
            return "Not selected.";
        }
        VisiblePlayerState state = snapshot.visibleState().orElseThrow();
        var classes = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        classes.addAll(state.selectedClasses().keySet());
        var nodes = new TreeSet<ResourceLocation>(ResourceLocation::compareNamespaced);
        nodes.addAll(state.nodeRanks().keySet());
        var abilities = new TreeMap<Integer, ResourceLocation>(state.abilitySlots());
        boolean classId = snapshot.activeDefinitions().orElseThrow().definitions().keySet().stream()
                .anyMatch(key -> key.kind().equals(DefinitionKinds.CLASS) && key.id().equals(selected));
        boolean abilityId = snapshot.activeDefinitions().orElseThrow().definitions().keySet().stream()
                .anyMatch(key -> key.kind().equals(DefinitionKinds.ABILITY) && key.id().equals(selected));
        if (classId) {
            if (!classes.remove(selected)) {
                classes.add(selected);
            }
        } else if (abilityId) {
            Optional<Integer> assigned = abilities.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(selected)).map(Map.Entry::getKey).findFirst();
            if (assigned.isPresent()) {
                abilities.remove(assigned.orElseThrow());
            } else {
                for (int slot = 0; slot < 8; slot++) {
                    if (!abilities.containsKey(slot)) {
                        abilities.put(slot, selected);
                        break;
                    }
                }
            }
        } else if (!nodes.remove(selected)) {
            nodes.add(selected);
        }
        return "Toggle " + selected + ". Classes " + classes + ". Nodes " + nodes
                + ". Abilities " + abilities + ". Local preview only. Server state is unchanged.";
    }

    private static String checkpoint(ClientNetworkState.Snapshot snapshot) {
        return snapshot.visibleState().map(state -> "generation " + state.definitionRevision().generation()
                + ", digest " + shortDigest(state.definitionRevision().semanticDigest())
                + ", sync " + state.syncRevision()).orElse("state unavailable");
    }

    private static void exportTests(ClientNetworkState.Snapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            var path = TestCenterState.export(checkpoint(snapshot));
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("Test Center results exported to " + path), false);
            }
        } catch (RuntimeException exception) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("Test Center export failed. " + exception.getMessage()), false);
            }
        }
    }

    private static String fingerprint(ClientNetworkState.Snapshot snapshot) {
        return snapshot.phase() + ":" + snapshot.definitionCount() + ":"
                + snapshot.visibleState().map(VisiblePlayerState::syncRevision).orElse(-1L) + ":"
                + snapshot.lastIntentResult().map(value -> value.requestId() + value.status().name()).orElse("") + ":"
                + snapshot.treeRefundPreview().map(value ->
                        value.requestId() + value.previewDigest()).orElse("") + ":"
                + snapshot.classChangePreview().map(value -> value.requestId() + value.previewDigest()).orElse("") + ":"
                + snapshot.carrierMigrationPreview().map(value -> value.requestId() + value.previewDigest()).orElse("");
    }

    private static String shortDigest(String value) {
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    public enum Tab {
        MAIN("Main Menu"),
        SKILLS("Skills"),
        TREES("Trees"),
        CLASSES("Classes"),
        ABILITIES("Abilities"),
        CLAIMS("Claims"),
        GUIDE("Guide"),
        COMPARE("Compare"),
        TESTS("Tests"),
        SYNC("Sync"),
        STUDIO("Studio");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Row(Optional<ResourceLocation> id, Optional<java.util.UUID> claimId, String label, String detail) {
    }

    private record ClassSlotModel(
            ResourceLocation id,
            Component display,
            DefinitionProjection.Entry entry,
            DefinitionProjection.ClassSlotView view
    ) {
    }

    private record SkillTreeModel(
            ResourceLocation id,
            Component display,
            DefinitionProjection.Entry entry,
            DefinitionProjection.TreeView tree
    ) {
    }

    private record PointBalance(
            ResourceLocation id,
            Component display,
            ItemStack icon,
            long balance
    ) {
    }

    private static final class TreeViewport {
        private double panX;
        private double panY;
        private double zoom = 1.0D;
        private boolean initialized;
    }

    private enum SkillNodeStatus {
        OWNED(AdvancementUi.NodeFrame.OWNED, "owned_node",
                "screen.progressiveskills.tree.status.owned"),
        AVAILABLE(AdvancementUi.NodeFrame.AVAILABLE, "available_node",
                "screen.progressiveskills.tree.status.available"),
        LOCKED(AdvancementUi.NodeFrame.LOCKED, "locked_node",
                "screen.progressiveskills.tree.status.locked"),
        SUSPENDED(AdvancementUi.NodeFrame.LOCKED, "locked_node",
                "screen.progressiveskills.tree.status.suspended");

        private final AdvancementUi.NodeFrame frame;
        private final String colorKey;
        private final String translationKey;

        SkillNodeStatus(
                AdvancementUi.NodeFrame frame,
                String colorKey,
                String translationKey
        ) {
            this.frame = frame;
            this.colorKey = colorKey;
            this.translationKey = translationKey;
        }

        Component label() {
            return Component.translatable(translationKey);
        }
    }
}
