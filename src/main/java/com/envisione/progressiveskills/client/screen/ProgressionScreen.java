package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.client.ClientPreferences;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
    private String fingerprint = "";
    private String query = "";
    private int openTicks;

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
        rows = rows(snapshot);
        selected = rows.isEmpty() ? 0 : Math.max(0, Math.min(selected, rows.size() - 1));
        int tabWidth = Math.max(44, Math.min(68, (width - 16) / 5));
        for (int index = 0; index < Tab.values().length; index++) {
            Tab value = Tab.values()[index];
            int x = 8 + index % 5 * tabWidth;
            int y = 28 + index / 5 * 22;
            Button button = addRenderableWidget(Button.builder(Component.literal(value.label), ignored -> selectTab(value))
                    .bounds(x, y, tabWidth - 2, 20).build());
            button.active = value != tab;
        }
        if (searchableTab()) {
            EditBox search = new EditBox(font, 10, 76, Math.max(120, Math.min(280, width - 20)), 20,
                    Component.literal("Search progression definitions"));
            search.setHint(Component.literal("Search names, ids, and details"));
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
        int contentTop = 100;
        int rowStep = ClientPreferences.compactLayout() ? 18 : 22;
        int rowHeight = ClientPreferences.compactLayout() ? 16 : 20;
        int actionReserve = width < 400 ? 110 : 62;
        int visibleRows = Math.max(1, (height - contentTop - actionReserve) / rowStep);
        int pages = Math.max(1, (rows.size() + visibleRows - 1) / visibleRows);
        page = Math.max(0, Math.min(page, pages - 1));
        int first = page * visibleRows;
        int last = Math.min(rows.size(), first + visibleRows);
        for (int index = first; index < last; index++) {
            Row row = rows.get(index);
            int rowIndex = index;
            Button button = addRenderableWidget(Button.builder(
                    Component.literal((index == selected ? "> " : "") + row.label()),
                    ignored -> {
                        selected = rowIndex;
                        rebuildWidgets();
                    }).bounds(10, contentTop + (index - first) * rowStep,
                            Math.max(120, width / 2 - 16), rowHeight).build());
            button.active = index != selected;
        }
        int bottom = height - 28;
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(10, bottom, 24, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(38, bottom, 24, 20).build());
        next.active = page + 1 < pages;
        addActions(snapshot, bottom);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width - 110, bottom, 100, 20).build());
        fingerprint = fingerprint(snapshot);
    }

    @Override
    public void tick() {
        super.tick();
        openTicks = Math.min(20, openTicks + 1);
        String current = fingerprint(PsNetworking.clientSnapshot());
        if (!current.equals(fingerprint)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        int alpha = ClientPreferences.reducedMotion() ? 0xE8 : Math.min(0xE8, 0x88 + openTicks * 5);
        int background = ClientPreferences.highContrast() ? 0xF0000000 : alpha << 24 | 0x181818;
        graphics.fill(4, 4, width - 4, height - 4, background);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        renderDetails(graphics, PsNetworking.clientSnapshot());
        super.render(graphics, mouseX, mouseY, partialTick);
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
            int action = 0;
            if (ability.filter(value -> value.kind().equals("toggle")).isPresent()) {
                addActionSlot(action++, y, "Toggle", () -> safe(
                        "Toggle " + id, () -> PsNetworking.sendAbilityToggle(id)));
            } else if (ability.filter(value -> value.kind().equals("active")).isPresent()
                    && assigned.isPresent()) {
                int slot = assigned.orElseThrow();
                addActionSlot(action++, y, "Activate", () -> safe(
                        "Activate " + id, () -> PsNetworking.sendAbilityActivate(slot)));
            }
            if (assigned.isPresent()) {
                int slot = assigned.orElseThrow();
                addActionSlot(action, y, "Unassign", () -> safe(
                        "Unassign " + id, () -> PsNetworking.sendAbilityUnassign(slot)));
            } else if (ability.filter(DefinitionProjection.AbilityView::slotAllowed).isPresent()) {
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

    private void addAction(int x, int y, String label, Runnable operation) {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> operation.run())
                .bounds(x, y, Math.max(70, font.width(label) + 12), 20).build());
    }

    private void addActionSlot(int index, int y, String label, Runnable operation) {
        int columns = Math.max(1, Math.min(4, (width - 190) / 100));
        int column = Math.floorMod(index, columns);
        int row = index / columns;
        addAction(70 + column * 100, y - row * 24, label, operation);
    }

    private void renderDetails(GuiGraphics graphics, ClientNetworkState.Snapshot snapshot) {
        float scale = ClientPreferences.textScale() / 100.0F;
        int x = Math.round(Math.max(132, width / 2 + 4) / scale);
        int y = Math.round(78 / scale);
        int maxWidth = Math.max(40, Math.round((width - Math.max(132, width / 2 + 4) - 12) / scale));
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.literal("No entries are available."), x, y, 0xAAAAAA, false);
            graphics.pose().popPose();
            return;
        }
        for (var line : font.split(Component.literal(rows.get(selected).detail()), maxWidth)) {
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
            int resultY = Math.round((height - 44) / scale);
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
            String display = value.getValue().display().map(DefinitionProjection.Text::fallback)
                    .orElse(value.getKey().id().getPath());
            String description = value.getValue().description().map(DefinitionProjection.Text::fallback)
                    .orElse("No description.");
            result.add(new Row(Optional.of(value.getKey().id()), Optional.empty(), display,
                    rowDetail(snapshot, value.getKey(), value.getValue(), description)));
        }
        result.sort(Comparator.comparing(Row::label, String.CASE_INSENSITIVE_ORDER));
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty()) {
            result.removeIf(row -> !row.label().toLowerCase(Locale.ROOT).contains(normalized)
                    && !row.detail().toLowerCase(Locale.ROOT).contains(normalized)
                    && row.id().map(ResourceLocation::toString).stream()
                    .noneMatch(value -> value.toLowerCase(Locale.ROOT).contains(normalized)));
        }
        return List.copyOf(result);
    }

    private boolean searchableTab() {
        return tab == Tab.SKILLS || tab == Tab.TREES || tab == Tab.CLASSES
                || tab == Tab.ABILITIES || tab == Tab.GUIDE;
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
        rebuildWidgets();
    }

    private void changePage(int amount) {
        page += amount;
        int rowStep = ClientPreferences.compactLayout() ? 18 : 22;
        selected = Math.min(rows.size() - 1, Math.max(0, page * Math.max(1, (height - 162) / rowStep)));
        rebuildWidgets();
    }

    private Optional<Row> selectedRow() {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(selected));
    }

    private static void safe(String label, java.util.function.BooleanSupplier sender) {
        SafeRetryTray.sendOrRemember(label, sender);
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
}
