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
        int gridLeft = frame.contentX() + 5;
        int gridTop = skillGridTop(frame);
        int buttonSize = 32;
        int gap = 5;
        int columns = skillColumns(frame);
        int visible = visibleEntries(frame);
        int first = page * visible;
        int last = Math.min(rows.size(), first + visible);
        for (int index = first; index < last; index++) {
            Row row = rows.get(index);
            int cell = index - first;
            int rowIndex = index;
            long level = row.id().map(id -> skillLevel(snapshot, id)).orElse(0L);
            ProgressionSelectorButton button = addRenderableWidget(new ProgressionSelectorButton(
                    gridLeft + cell % columns * (buttonSize + gap),
                    gridTop + cell / columns * (buttonSize + gap),
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        String detail = rows.isEmpty() ? "No entries." : rows.get(selected).detail();
        return Component.literal(title.getString() + ". " + tab.label + ". " + detail);
    }

    private void addActions(ClientNetworkState.Snapshot snapshot, int y) {
        if (tab == Tab.TREES) {
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
        int top = frame.contentY() + 2;
        int dashboardBottom = skillGridTop(frame) - 3;
        int left = frame.contentX() + 2;
        int right = frame.contentRight() - 2;
        int leftWidth = Math.clamp(frame.contentWidth() / 4, 78, 104);
        int centerWidth = Math.clamp(frame.contentWidth() / 5, 54, 78);
        int centerLeft = left + leftWidth + 3;
        int centerRight = centerLeft + centerWidth;
        int detailLeft = centerRight + 3;
        AdvancementUi.renderInset(graphics, left, top, centerLeft - 3, dashboardBottom);
        AdvancementUi.renderInset(graphics, centerLeft, top, centerRight, dashboardBottom);
        AdvancementUi.renderInset(graphics, detailLeft, top, right, dashboardBottom);
        AdvancementUi.renderInset(graphics, left, skillGridTop(frame) - 1,
                right, frame.contentBottom() - 2);
        long totalLevel = rows.stream().flatMap(row -> row.id().stream())
                .mapToLong(id -> skillLevel(snapshot, id)).sum();
        long highest = rows.stream().flatMap(row -> row.id().stream())
                .mapToLong(id -> skillLevel(snapshot, id)).max().orElse(0L);
        int textX = left + 5;
        int textY = top + 5;
        graphics.drawString(font, Component.translatable("screen.progressiveskills.skills.total_level"),
                textX, textY, 0xFFFFD65C, false);
        graphics.drawString(font, Long.toString(totalLevel), textX, textY + 11, 0xFFFFFFFF, true);
        graphics.drawString(font, Component.translatable("screen.progressiveskills.skills.tracks"),
                textX, textY + 25, 0xFFB8B8B8, false);
        graphics.drawString(font, Integer.toString(rows.size()), textX, textY + 36, 0xFFFFFFFF, true);
        if (dashboardBottom - top >= 78) {
            graphics.drawString(font, Component.translatable("screen.progressiveskills.skills.highest"),
                    textX, textY + 50, 0xFFB8B8B8, false);
            graphics.drawString(font, Long.toString(highest), textX, textY + 61, 0xFFFFFFFF, true);
        }
        if (minecraft != null && minecraft.player != null) {
            int scale = Math.clamp((dashboardBottom - top) / 3, 18, 30);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    centerLeft + 2,
                    top + 2,
                    centerRight - 2,
                    dashboardBottom - 2,
                    scale,
                    0.0F,
                    mouseX,
                    mouseY,
                    minecraft.player
            );
        }
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.literal("No skills are available."),
                    detailLeft + 5, top + 5, 0xFFAAAAAA, false);
            return;
        }
        int preview = previewEntryIndex();
        Row row = rows.get(preview);
        int x = detailLeft + 5;
        int y = top + 5;
        int maxWidth = Math.max(36, right - x - 4);
        graphics.renderItem(rowIcon(snapshot, row), x, y);
        x += 21;
        var nameLines = font.split(UiText.legacy(row.label()), Math.max(20, maxWidth - 21));
        for (int lineIndex = 0; lineIndex < Math.min(2, nameLines.size()); lineIndex++) {
            graphics.drawString(font, nameLines.get(lineIndex), x, y + 3, 0xFFFFFFFF, false);
            y += font.lineHeight + 1;
        }
        x = detailLeft + 5;
        y = Math.max(y + 3, top + 24);
        ResourceLocation id = row.id().orElseThrow();
        graphics.drawString(font, Component.translatable(
                        "screen.progressiveskills.skills.level", skillLevel(snapshot, id)),
                x, y, 0xFFFFD65C, false);
        y += 12;
        graphics.drawString(font, Component.translatable(
                        "screen.progressiveskills.skills.active_xp", skillBalance(snapshot, id, true)),
                x, y, 0xFF80C8FF, false);
        y += 11;
        graphics.drawString(font, Component.translatable(
                        "screen.progressiveskills.skills.banked_xp", skillBalance(snapshot, id, false)),
                x, y, 0xFFB8B8B8, false);
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
            int vertical = Math.max(1, (frame.contentBottom() - skillGridTop(frame) - 4) / 37);
            return vertical * skillColumns(frame);
        }
        if (tab == Tab.CLASSES) {
            int vertical = Math.max(1, (frame.contentBottom() - frame.contentY() - 32) / 41);
            return vertical * classColumns(frame);
        }
        int contentTop = frame.contentY() + (searchableTab() ? 27 : 5);
        int vertical = Math.max(1, (frame.contentBottom() - contentTop - 4) / 44);
        return vertical * cardColumns(frame);
    }

    static int skillGridTop(AdvancementUi.Frame frame) {
        return frame.contentY() + Math.clamp(frame.contentHeight() * 45 / 100, 62, 82);
    }

    static int skillColumns(AdvancementUi.Frame frame) {
        return Math.max(1, (frame.contentWidth() - 10) / 37);
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
}
