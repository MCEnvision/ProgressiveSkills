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
import com.envisione.progressiveskills.common.skill.SkillStateIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProgressionScreen extends ProgressiveScreen {
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
    private ResourceLocation inspectedSkillNode;
    private int renderedSkillIndex = -1;

    public ProgressionScreen() {
        this(Tab.SKILLS);
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
        if (tab == Tab.SKILLS) {
            addSkillSelectors(snapshot, frame);
        } else if (tab == Tab.CLASSES) {
            addClassSelectors(snapshot, frame);
        } else {
            addCardBrowser(snapshot, frame);
        }
        int bottom = frame.footerY();
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(frame.x(), bottom, 24, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(frame.x() + 26, bottom, 24, 20).build());
        next.active = page + 1 < pages;
        addActions(snapshot, bottom);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(frame.right() - 100, bottom, 100, 20).build());
        fingerprint = fingerprint(snapshot);
    }

    private void addProgressionTabs(AdvancementUi.Frame frame) {
        for (int index = 0; index < Tab.values().length; index++) {
            Tab value = Tab.values()[index];
            boolean above = index < 8;
            int local = above ? index : index - 8;
            int count = above ? Math.min(8, Tab.values().length) : Tab.values().length - 8;
            AdvancementTabButton.Side side = above
                    ? AdvancementTabButton.Side.ABOVE : AdvancementTabButton.Side.LEFT;
            AdvancementTabButton.Position position = local == 0
                    ? AdvancementTabButton.Position.FIRST
                    : local == count - 1 ? AdvancementTabButton.Position.LAST
                    : AdvancementTabButton.Position.MIDDLE;
            int x = above ? frame.x() + local * 28 : frame.x() - 28;
            int y = above ? frame.y() - 28 : frame.y() + local * 28;
            addRenderableWidget(new AdvancementTabButton(
                    x, y, Component.literal(value.label), tabIcon(value), value == tab,
                    side, position, () -> selectTab(value)));
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
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        AdvancementUi.Frame frame = AdvancementUi.largeFrame(width, height);
        AdvancementUi.renderInside(graphics, frame);
        if (tab == Tab.SKILLS) {
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
        int pages = Math.max(1, (rows.size() + visibleEntries(frame) - 1) / visibleEntries(frame));
        int pageCenter = tab == Tab.CLASSES
                ? frame.contentX() + classSlotWidth(frame) + Math.max(20,
                (detailLeft(frame) - frame.contentX() - classSlotWidth(frame)) / 2)
                : tab == Tab.SKILLS ? frame.contentX() + frame.contentWidth() / 2
                : frame.contentX() + cardAreaWidth(frame) / 2;
        graphics.drawCenteredString(font, Component.literal((page + 1) + " of " + pages),
                pageCenter, frame.footerY() + 6, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.SKILLS && button == 0) {
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
                    rebuildWidgets();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
                        addAction(details.left() + 3, details.bottom() - 21, "Open tree", () ->
                                        Minecraft.getInstance().setScreen(new TreeScreen(tree.id())),
                                Math.max(48, details.width() - 6)));
            }
        } else if (tab == Tab.TREES) {
            addActionSlot(0, y, "Open tree", () -> Minecraft.getInstance().setScreen(new TreeScreen()));
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
        renderedSkillIndex = -1;
        if (rows.isEmpty()) {
            renderSkillDossier(graphics, layout, snapshot, Optional.empty(), Optional.empty(), mouseX, mouseY);
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
        renderSkillDossier(graphics, layout, snapshot, Optional.of(skillId), tree, mouseX, mouseY);
        renderSkillHeader(graphics, layout.header(), snapshot, row, skillId, tree);
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
            Optional<ResourceLocation> selectedSkill,
            Optional<SkillTreeModel> selectedTree,
            int mouseX,
            int mouseY
    ) {
        ProgressionWorkbenchLayout.Rect meter = layout.meter();
        long totalLevel = rows.stream().flatMap(row -> row.id().stream())
                .mapToLong(id -> skillLevel(snapshot, id)).sum();
        long level = selectedSkill.map(id -> skillLevel(snapshot, id)).orElse(0L);
        long activeXp = selectedSkill.map(id -> skillBalance(snapshot, id, true)).orElse(0L);
        long bankedXp = selectedSkill.map(id -> skillBalance(snapshot, id, false)).orElse(0L);
        int textX = meter.left() + 2;
        int textWidth = Math.max(8, meter.width() - 4);
        Component total = Component.translatable(
                "screen.progressiveskills.skills.total_level_value", totalLevel);
        graphics.drawString(font, font.plainSubstrByWidth(total.getString(), textWidth),
                textX, meter.top() + 1, 0xFFFFD65C, false);
        Component levelText = Component.translatable("screen.progressiveskills.skills.level", level);
        graphics.drawString(font, font.plainSubstrByWidth(levelText.getString(), textWidth),
                textX, meter.top() + 12, 0xFFFFFFFF, false);
        int barLeft = textX;
        int barRight = meter.right() - 2;
        int barTop = meter.top() + 23;
        graphics.fill(barLeft, barTop, barRight, barTop + 6, 0xD0181818);
        double share = ProgressionWorkbenchLayout.activeShare(activeXp, bankedXp);
        int fill = (int) Math.round((barRight - barLeft) * share);
        if (fill > 0) {
            graphics.fill(barLeft, barTop, barLeft + fill, barTop + 6,
                    theme.workbenchColor("meter_fill"));
        }
        if (meter.height() >= 40) {
            String xp = Component.translatable(
                    "screen.progressiveskills.skills.xp_balance", activeXp, bankedXp).getString();
            graphics.drawString(font, font.plainSubstrByWidth(xp, textWidth),
                    textX, barTop + 8, 0xFFB8B8B8, false);
        }

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
            ResourceLocation skillId,
            Optional<SkillTreeModel> tree
    ) {
        int iconX = header.left() + 5;
        int iconY = header.top() + Math.max(2, (header.height() - 16) / 2);
        graphics.renderItem(rowIcon(snapshot, row), iconX, iconY);
        int textX = iconX + 21;
        int availableWidth = Math.max(20, header.right() - textX - 5);
        String name = font.plainSubstrByWidth(UiText.legacy(row.label()).getString(), availableWidth);
        graphics.drawString(font, name, textX, header.top() + 4, 0xFFFFFFFF, false);
        String subtitle = Component.translatable(
                "screen.progressiveskills.skills.level", skillLevel(snapshot, skillId)).getString();
        if (tree.isPresent()) {
            subtitle += ". " + tree.orElseThrow().display().getString();
        }
        graphics.drawString(font, font.plainSubstrByWidth(subtitle, availableWidth),
                textX, header.top() + 14, 0xFFB8B8B8, false);
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
        List<ProgressionWorkbenchLayout.NodeAnchor<ResourceLocation>> anchors = tree.tree().nodes().stream()
                .map(node -> new ProgressionWorkbenchLayout.NodeAnchor<>(
                        node.id(), node.row(), node.column())).toList();
        skillNodeCenters = ProgressionWorkbenchLayout.fitNodes(anchors, layout.graph());
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

    private static long skillBalance(
            ClientNetworkState.Snapshot snapshot,
            ResourceLocation id,
            boolean active
    ) {
        return snapshot.visibleState().map(state -> state.balances().getOrDefault(
                (active ? SkillStateIds.activeXp(id) : SkillStateIds.bankedXp(id)).toString(), 0L
        )).orElse(0L);
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
                + snapshot.classChangePreview().map(value -> value.requestId() + value.previewDigest()).orElse("") + ":"
                + snapshot.carrierMigrationPreview().map(value -> value.requestId() + value.previewDigest()).orElse("");
    }

    private static String shortDigest(String value) {
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    public enum Tab {
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
