package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.network.DefinitionProjection;
import com.envisione.progressiveskills.common.network.PsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StudioScreen extends Screen {
    private final List<String> history = new ArrayList<>();
    private EditBox draft;
    private EditBox revision;
    private EditBox definition;
    private EditBox display;
    private EditBox description;
    private EditBox fields;
    private EditBox command;
    private Button kindButton;
    private int kindIndex;
    private int contentTop = 132;
    private final String initialDraft;
    private final String initialRevision;
    private final String initialDefinition;
    private final String initialDisplay;

    public StudioScreen() {
        this("", "0", "progressiveskills:new_definition", "New Definition");
    }

    StudioScreen(String draft, String revision, String definition, String display) {
        super(Component.literal("ProgressiveSkills Studio"));
        this.initialDraft = draft;
        this.initialRevision = revision;
        this.initialDefinition = definition;
        this.initialDisplay = display;
    }

    @Override
    protected void init() {
        int formWidth = Math.min(620, Math.max(240, width - 24));
        draft = field(12, 30, Math.max(120, formWidth / 3), "Draft id", initialDraft);
        revision = field(draft.getX() + draft.getWidth() + 4, 30, 68, "Revision", initialRevision);
        definition = field(revision.getX() + revision.getWidth() + 4, 30,
                12 + formWidth - revision.getX() - revision.getWidth() - 4,
                "Definition id", initialDefinition);
        display = field(12, 54, Math.max(120, formWidth / 3), "Display name", initialDisplay);
        description = field(display.getX() + display.getWidth() + 4, 54,
                formWidth - display.getWidth() - 4, "Description", "Studio authored definition");
        fields = field(12, 78, formWidth, "Additional JSON fields", defaultFields(kind()));
        fields.setMaxLength(4096);

        int buttonGap = 3;
        boolean compactControls = formWidth < 480;
        int buttonWidth = compactControls
                ? Math.max(40, (formWidth - buttonGap * 3) / 4)
                : Math.max(40, Math.min(64, (formWidth - 105) / 7));
        int kindWidth = compactControls
                ? buttonWidth
                : formWidth - buttonWidth * 7 - buttonGap * 7;
        int buttonX = 12;
        int buttonY = 102;
        addRenderableWidget(Button.builder(Component.literal("Previous"), ignored -> changeKind(-1))
                .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + buttonGap;
        kindButton = addRenderableWidget(Button.builder(kindLabel(), ignored -> changeKind(1))
                .bounds(buttonX, buttonY, kindWidth, 20).build());
        buttonX += kindButton.getWidth() + buttonGap;
        addRenderableWidget(Button.builder(Component.literal("Next"), ignored -> changeKind(1))
                .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + buttonGap;
        addRenderableWidget(Button.builder(Component.literal("Create file"), ignored -> createFromForm())
                .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + buttonGap;
        if (compactControls) {
            buttonX = 12;
            buttonY += 24;
            contentTop = 156;
        } else {
            contentTop = 132;
        }
        addRenderableWidget(Button.builder(Component.literal("Lint"), ignored -> draftCommand("lint"))
                .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + buttonGap;
        addRenderableWidget(Button.builder(Component.literal("Diff"), ignored -> draftCommand("diff"))
                .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + buttonGap;
        addRenderableWidget(Button.builder(Component.literal("History"), ignored -> draftCommand("history"))
                .bounds(buttonX, buttonY, buttonWidth, 20).build());
        buttonX += buttonWidth + buttonGap;
        addRenderableWidget(Button.builder(Component.literal("Rebase"), ignored -> rebase())
                .bounds(buttonX, buttonY, buttonWidth, 20).build());

        command = field(12, height - 28, Math.max(40, width - 316), "Studio command", "ps studio ");
        command.setMaxLength(8192);
        addRenderableWidget(Button.builder(Component.literal("Pack"), ignored -> draftCommand("export"))
                .bounds(width - 298, height - 28, 46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), ignored -> openPreview())
                .bounds(width - 248, height - 28, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Graph"), ignored -> openGraph())
                .bounds(width - 180, height - 28, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Run"), ignored -> runCommand())
                .bounds(width - 106, height - 28, 46, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width - 56, height - 28, 46, 20).build());
        setInitialFocus(draft);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(6, 6, width - 6, height - 6, 0xEE151515);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        drawGraph(graphics);
        int historyX = Math.max(12, width - 250);
        int historyY = contentTop;
        graphics.drawString(font, Component.literal("Command results in chat"), historyX, historyY,
                0x88C8FF, false);
        historyY += 13;
        for (int index = Math.max(0, history.size() - 8); index < history.size(); index++) {
            String value = history.get(index);
            if (value.length() > 38) {
                value = value.substring(0, 35) + "...";
            }
            graphics.drawString(font, Component.literal(value), historyX, historyY, 0xA8C8E8, false);
            historyY += font.lineHeight + 2;
        }
        graphics.drawString(font, Component.literal(
                "Forms write revision checked JSON. Publishing still requires a confirmed lint digest."),
                12, height - 42, 0xFFCC66, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && getFocused() == command) {
            runCommand();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        PsNetworking.consumeStudioFileResult().ifPresent(result -> {
            if (draft == null || !result.draftId().toString().equals(draft.getValue().strip())) {
                return;
            }
            if (result.success()) {
                revision.setValue(Long.toString(result.revision()));
                history.add("Draft revision " + result.revision() + ".");
            } else {
                history.add("Studio write failed. " + result.message());
            }
            trimHistory();
        });
    }

    private EditBox field(int x, int y, int fieldWidth, String hint, String value) {
        EditBox box = new EditBox(font, x, y, Math.max(40, fieldWidth), 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(512);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private DefinitionKind kind() {
        return DefinitionKinds.all().get(Math.floorMod(kindIndex, DefinitionKinds.all().size()));
    }

    private Component kindLabel() {
        return Component.literal("Kind " + kind().id().getPath());
    }

    private void changeKind(int amount) {
        kindIndex = Math.floorMod(kindIndex + amount, DefinitionKinds.all().size());
        kindButton.setMessage(kindLabel());
        fields.setValue(defaultFields(kind()));
    }

    private void createFromForm() {
        try {
            ResourceLocation draftId = ResourceLocation.parse(draft.getValue().strip());
            ResourceLocation definitionId = ResourceLocation.parse(definition.getValue().strip());
            if (!draftId.getNamespace().equals(definitionId.getNamespace())) {
                throw new IllegalArgumentException("Draft and definition namespaces must match");
            }
            long draftRevision = Long.parseLong(revision.getValue().strip());
            if (draftRevision < 0) {
                throw new IllegalArgumentException("Draft revision must not be negative");
            }
            if (fields.getValue().isBlank() && !supportsPresentation(kind())) {
                throw new IllegalArgumentException("This definition kind requires additional fields");
            }
            String path = kind().sourceDirectory() + "/" + definitionId.getPath() + ".json";
            String json = definitionJson(kind(), definitionId, display.getValue(),
                    description.getValue(), fields.getValue());
            PsNetworking.sendStudioFilePut(draftId, draftRevision, path, json);
            history.add("Studio file put " + draftId + ".");
            trimHistory();
        } catch (RuntimeException exception) {
            history.add("Form error. " + safeMessage(exception));
            trimHistory();
        }
    }

    private void draftCommand(String operation) {
        String draftId = draft.getValue().strip();
        if (!draftId.isEmpty()) {
            send("ps studio " + operation + " " + draftId);
        }
    }

    private void rebase() {
        String draftId = draft.getValue().strip();
        String draftRevision = revision.getValue().strip();
        if (!draftId.isEmpty() && !draftRevision.isEmpty()) {
            send("ps studio rebase " + draftId + " " + draftRevision);
        }
    }

    private void runCommand() {
        String value = command.getValue().strip();
        if (!value.isEmpty()) {
            send(value);
            command.setValue("ps studio ");
        }
    }

    private void openGraph() {
        try {
            ResourceLocation draftId = ResourceLocation.parse(draft.getValue().strip());
            ResourceLocation definitionId = ResourceLocation.parse(definition.getValue().strip());
            long draftRevision = Long.parseLong(revision.getValue().strip());
            if (draftRevision < 0 || !draftId.getNamespace().equals(definitionId.getNamespace())) {
                throw new IllegalArgumentException("Graph draft identity or revision is invalid");
            }
            Minecraft.getInstance().setScreen(new StudioGraphScreen(
                    draftId, draftRevision, definitionId, display.getValue()));
        } catch (RuntimeException exception) {
            history.add("Graph error. " + safeMessage(exception));
            trimHistory();
        }
    }

    private void openPreview() {
        Minecraft.getInstance().setScreen(new StudioCurveScreen(
                draft.getValue().strip(), revision.getValue().strip(),
                definition.getValue().strip(), display.getValue()));
    }

    private void send(String value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String normalized = value.startsWith("/") ? value.substring(1) : value;
        minecraft.player.connection.sendCommand(normalized);
        history.add(normalized);
        trimHistory();
    }

    private void trimHistory() {
        while (history.size() > 32) {
            history.removeFirst();
        }
    }

    private void drawGraph(GuiGraphics graphics) {
        int left = 12;
        int top = contentTop;
        int right = Math.max(left + 120, width - 260);
        int bottom = height - 50;
        graphics.drawString(font, Component.literal("Live dependency graph"), left, top, 0x7CFC98, false);
        top += 14;
        var definitions = PsNetworking.clientSnapshot().activeDefinitions();
        if (definitions.isEmpty()) {
            graphics.drawString(font, Component.literal("No synchronized definitions"), left, top,
                    0xC8C8C8, false);
            return;
        }
        var treeEntry = definitions.orElseThrow().definitions().entrySet().stream()
                .filter(entry -> entry.getValue().tree().isPresent()).findFirst();
        if (treeEntry.isPresent()) {
            drawTreeGraph(graphics, treeEntry.orElseThrow().getKey().id().toString(),
                    treeEntry.orElseThrow().getValue().tree().orElseThrow(), left, top, right, bottom);
            return;
        }
        int x = left;
        int y = top;
        for (var entry : definitions.orElseThrow().definitions().entrySet()) {
            int nodeWidth = Math.min(130, Math.max(70, font.width(entry.getKey().id().getPath()) + 10));
            if (x + nodeWidth > right) {
                x = left;
                y += 24;
            }
            if (y + 20 > bottom) {
                break;
            }
            graphics.fill(x, y, x + nodeWidth, y + 18, 0xFF26384A);
            graphics.drawString(font, Component.literal(entry.getKey().id().getPath()),
                    x + 4, y + 5, 0xFFFFFF, false);
            x += nodeWidth + 6;
        }
    }

    private void drawTreeGraph(
            GuiGraphics graphics,
            String name,
            DefinitionProjection.TreeView tree,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.drawString(font, Component.literal(name), left, top, 0xFFFFFF, false);
        int graphTop = top + 14;
        int cellWidth = 96;
        int cellHeight = 38;
        int minimumRow = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::row).min().orElse(0);
        int minimumColumn = tree.nodes().stream().mapToInt(DefinitionProjection.NodeView::column).min().orElse(0);
        var positions = new LinkedHashMap<ResourceLocation, int[]>();
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            int x = left + Math.floorMod(node.column() - minimumColumn, Math.max(1, (right - left) / cellWidth))
                    * cellWidth;
            int y = graphTop + (node.row() - minimumRow) * cellHeight;
            if (y < bottom - 20) {
                positions.put(node.id(), new int[]{x, y});
            }
        }
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            int[] to = positions.get(node.id());
            if (to == null) {
                continue;
            }
            List<ResourceLocation> dependencies = new ArrayList<>(node.requires());
            dependencies.addAll(node.requiresAny());
            for (ResourceLocation dependency : dependencies) {
                int[] from = positions.get(dependency);
                if (from != null) {
                    int color = node.requires().contains(dependency) ? 0xFF80B0D0 : 0xFFE0B060;
                    graphics.fill(Math.min(from[0] + 38, to[0] + 39), from[1] + 17,
                            Math.max(from[0] + 38, to[0] + 39), from[1] + 19, color);
                    graphics.fill(to[0] + 37, Math.min(from[1] + 17, to[1]),
                            to[0] + 39, Math.max(from[1] + 19, to[1] + 1), color);
                }
            }
        }
        for (Map.Entry<ResourceLocation, int[]> entry : positions.entrySet()) {
            int[] position = entry.getValue();
            graphics.fill(position[0], position[1], position[0] + 78, position[1] + 19, 0xFF26384A);
            String label = entry.getKey().getPath();
            int slash = label.lastIndexOf('/');
            label = slash >= 0 ? label.substring(slash + 1) : label;
            if (label.length() > 11) {
                label = label.substring(0, 10) + ".";
            }
            graphics.drawString(font, Component.literal(label), position[0] + 4, position[1] + 5,
                    0xFFFFFF, false);
        }
    }

    private String definitionJson(
            DefinitionKind kind,
            ResourceLocation id,
            String display,
            String description,
            String additionalFields
    ) {
        String fields = additionalFields.strip();
        if (!fields.isEmpty() && !fields.startsWith(",")) {
            fields = "," + fields;
        }
        String presentation = supportsPresentation(kind)
                ? "    \"display\": {\"fallback\": \"" + json(display) + "\"},\n"
                + "    \"description\": {\"fallback\": \"" + json(description) + "\"},\n"
                + "    \"icon\": {\"type\": \"item\", \"value\": \"minecraft:knowledge_book\", "
                + "\"fallback\": \"minecraft:barrier\", \"alt\": \"" + json(display) + "\"}"
                : "";
        if (presentation.isEmpty() && fields.startsWith(",")) {
            fields = fields.substring(1);
        }
        String companion = companionJson(kind, id, display);
        return "{\n"
                + "  \"schema_version\": 2,\n"
                + "  \"" + json(kind.id().getPath()) + "\": {\n"
                + "    \"id\": \"" + json(id.toString()) + "\",\n"
                + presentation
                + fields + "\n  }" + companion + "\n}";
    }

    private String defaultFields(DefinitionKind kind) {
        ResourceLocation definitionId = definitionId();
        ResourceLocation skill = firstDefinitionId(DefinitionKinds.SKILL, "progressiveskills:physique");
        ResourceLocation currency = firstDefinitionId(
                DefinitionKinds.CURRENCY, "progressiveskills:global_points");
        if (kind.equals(DefinitionKinds.CURRENCY)) {
            return "\"minimum\":0,\"maximum\":1000000,\"initial\":0,\"scope\":\"character\"";
        }
        if (kind.equals(DefinitionKinds.CLASS_SLOT)) {
            return "\"capacity\":1,\"swap_policy\":\"allowed\"";
        }
        if (kind.equals(DefinitionKinds.ABILITY)) {
            return "\"enabled\":true,\"kind\":\"active\",\"slot_allowed\":true,"
                    + "\"cooldown_ticks\":20,\"max_charges\":1,\"recharge_ticks\":20";
        }
        if (kind.equals(DefinitionKinds.SKILL)) {
            return "\"max_level\":10,\"enabled\":true,\"overflow\":\"bank\","
                    + "\"negative_xp_policy\":\"deny\"";
        }
        if (kind.equals(DefinitionKinds.CLASS)) {
            ResourceLocation slot = firstDefinitionId(
                    DefinitionKinds.CLASS_SLOT, "progressiveskills:combat");
            return "\"enabled\":true,\"access_required\":false,\"slot\":\"" + json(slot.toString())
                    + "\",\"slot_cost\":1,\"exclusive_tags\":[],\"prerequisites\":{"
                    + "\"min_level\":{},\"nodes\":[],\"classes\":[]},\"respec_allowed\":true";
        }
        if (kind.equals(DefinitionKinds.TREE)) {
            return "\"enabled\":true,\"scope\":\"global\",\"currency\":\""
                    + json(currency.toString()) + "\",\"dependency_policy\":\"cascade_refund\"";
        }
        if (kind.equals(DefinitionKinds.ITEM)) {
            return "\"enabled\":true,\"carrier\":\"progressiveskills:tome\","
                    + "\"behavior_version\":1,\"migration_policy\":\"keep_pinned\","
                    + "\"bind\":\"none\",\"delivery_policy\":\"pending_claim\","
                    + "\"rarity\":\"common\",\"glint\":false,\"stack_size\":16,"
                    + "\"charges\":1,\"cooldown_ticks\":0";
        }
        if (kind.equals(DefinitionKinds.RULE)) {
            return "\"enabled\":true,\"trigger\":\"progressiveskills:block_break\","
                    + "\"priority\":0,\"stack_group\":\"" + json(definitionId.toString())
                    + "\",\"stack_rule\":\"sum\",\"credit\":\"actor\","
                    + "\"match\":[\"tag:minecraft:logs\"],\"base\":1,\"rounding\":\"floor\","
                    + "\"anti_exploit\":{\"fake_players\":\"deny\","
                    + "\"allowed_block_origins\":[\"natural\",\"creative_placed\"],"
                    + "\"cooldown_ticks\":0,\"repeat_window_ticks\":0,"
                    + "\"repeat_decay\":1.0,\"minimum_multiplier\":1.0},"
                    + "\"outputs\":[{\"id\":\"" + json(childId(definitionId, "xp").toString())
                    + "\",\"type\":\"xp\",\"skill\":\"" + json(skill.toString())
                    + "\",\"amount_formula\":\"rule_amount\"}]";
        }
        if (kind.equals(DefinitionKinds.COMPONENT_SPEC)) {
            return "\"fallback\":\"Studio text\"";
        }
        if (kind.equals(DefinitionKinds.ICON_SPEC)) {
            return "\"type\":\"item\",\"value\":\"minecraft:knowledge_book\","
                    + "\"fallback\":\"minecraft:barrier\",\"alt\":\"Studio icon\"";
        }
        if (kind.equals(DefinitionKinds.COMPATIBILITY_PROFILE)) {
            return "\"mode\":\"best_effort\",\"required\":[],\"preferred\":[],\"active\":false";
        }
        if (kind.equals(DefinitionKinds.VARIABLE)) {
            return "\"value\":0";
        }
        if (kind.equals(DefinitionKinds.RESOURCE)) {
            return "\"minimum\":0,\"maximum\":100,\"initial\":0";
        }
        if (kind.equals(DefinitionKinds.CONVERSION)) {
            List<ResourceLocation> currencies = definitionIds(DefinitionKinds.CURRENCY);
            ResourceLocation target = currencies.stream().filter(value -> !value.equals(currency)).findFirst()
                    .orElse(ResourceLocation.fromNamespaceAndPath("progressiveskills", "secondary_points"));
            return "\"from\":\"" + json(currency.toString()) + "\",\"to\":\""
                    + json(target.toString()) + "\",\"numerator\":1,\"denominator\":1";
        }
        if (kind.equals(DefinitionKinds.GRANT_BUNDLE)) {
            return "\"grants\":[{\"currency\":\"" + json(currency.toString())
                    + "\",\"amount\":1}]";
        }
        if (kind.equals(DefinitionKinds.TRAINING_CONTRACT)) {
            return "\"minimum_goal\":1,\"maximum_goal\":10,\"eligible_skills\":[\""
                    + json(skill.toString()) + "\"]";
        }
        if (kind.equals(DefinitionKinds.COMBO_MASTERY)) {
            return "\"sequence\":[\"" + json(skill.toString()) + "\",\""
                    + json(skill.toString()) + "\"],\"timeout_ticks\":200,\"cooldown_ticks\":20";
        }
        if (kind.equals(DefinitionKinds.REACTIVE_PROC)) {
            return "\"trigger\":\"progressiveskills:block_break\",\"chance_basis_points\":10000";
        }
        if (kind.equals(DefinitionKinds.PRESTIGE)) {
            return "\"minimum_total_level\":0,\"reward_currency_amount\":1";
        }
        if (kind.equals(DefinitionKinds.TREE_RANK) || kind.equals(DefinitionKinds.CLASS_RANK)) {
            return "\"maximum_rank\":1,\"base_cost\":1,\"cost_growth\":0,"
                    + "\"cost_currency\":\"" + json(currency.toString()) + "\"";
        }
        if (kind.equals(DefinitionKinds.STANCE)) {
            return "\"group\":\"progressiveskills:studio_stances\"";
        }
        if (kind.equals(DefinitionKinds.CHALLENGE)) {
            return "\"goal\":10,\"progress_per_xp\":1,\"skill\":\""
                    + json(skill.toString()) + "\"";
        }
        if (kind.equals(DefinitionKinds.PREDICATE)) {
            return "\"type\":\"flag\",\"flag\":\"progressiveskills:studio_flag\"";
        }
        if (kind.equals(DefinitionKinds.TEMPLATE)) {
            return "\"target_kind\":\"progressiveskills:skill\",\"extends\":[],"
                    + "\"fields\":{\"enabled\":true}";
        }
        return "\"enabled\":true";
    }

    private String companionJson(DefinitionKind kind, ResourceLocation id, String display) {
        if (kind.equals(DefinitionKinds.SKILL)) {
            return ",\n  \"curve\": {\"type\":\"linear\",\"base\":100,\"step\":25,"
                    + "\"rounding\":\"ceil\"}";
        }
        if (kind.equals(DefinitionKinds.ABILITY)) {
            return ",\n  \"targeting\": {\"mode\":\"self\"},\n"
                    + "  \"actions\": [{\"id\":\"" + json(childId(id, "activate").toString())
                    + "\",\"type\":\"message\",\"message\":{\"fallback\":\""
                    + json(display + " activated") + "\"}}]";
        }
        if (kind.equals(DefinitionKinds.TREE)) {
            ResourceLocation node = childId(id, "root");
            return ",\n  \"nodes\": [{\"id\":\"" + json(node.toString())
                    + "\",\"display\":{\"fallback\":\"Root\"},"
                    + "\"description\":{\"fallback\":\"Starting node\"},"
                    + "\"icon\":{\"type\":\"item\",\"value\":\"minecraft:knowledge_book\","
                    + "\"fallback\":\"minecraft:barrier\",\"alt\":\"Root\"},"
                    + "\"cost\":1,\"row\":0,\"col\":0,\"requires\":[],"
                    + "\"requires_any\":[],\"min_level\":{},\"grants\":[]}]";
        }
        if (kind.equals(DefinitionKinds.ITEM)) {
            ResourceLocation skill = firstDefinitionId(
                    DefinitionKinds.SKILL, "progressiveskills:physique");
            return ",\n  \"use_actions\": [{\"id\":\"" + json(childId(id, "grant_xp").toString())
                    + "\",\"type\":\"xp\",\"skill\":\"" + json(skill.toString())
                    + "\",\"amount\":1,\"consume\":1}]";
        }
        return "";
    }

    private static boolean supportsPresentation(DefinitionKind kind) {
        return !kind.equals(DefinitionKinds.RULE)
                && !kind.equals(DefinitionKinds.COMPONENT_SPEC)
                && !kind.equals(DefinitionKinds.ICON_SPEC)
                && !kind.equals(DefinitionKinds.COMPATIBILITY_PROFILE);
    }

    private ResourceLocation definitionId() {
        if (definition != null) {
            try {
                return ResourceLocation.parse(definition.getValue().strip());
            } catch (RuntimeException ignored) {
                return ResourceLocation.fromNamespaceAndPath("progressiveskills", "new_definition");
            }
        }
        return ResourceLocation.fromNamespaceAndPath("progressiveskills", "new_definition");
    }

    private ResourceLocation firstDefinitionId(DefinitionKind kind, String fallback) {
        return definitionIds(kind).stream().findFirst().orElse(ResourceLocation.parse(fallback));
    }

    private List<ResourceLocation> definitionIds(DefinitionKind kind) {
        return PsNetworking.clientSnapshot().activeDefinitions()
                .map(projection -> projection.definitions().keySet().stream()
                        .filter(key -> key.kind().equals(kind))
                        .map(key -> key.id())
                        .toList())
                .orElse(List.of());
    }

    private static ResourceLocation childId(ResourceLocation owner, String child) {
        return ResourceLocation.fromNamespaceAndPath(owner.getNamespace(), owner.getPath() + "/" + child);
    }

    private static String json(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32) {
                        throw new IllegalArgumentException("Studio form contains a control character");
                    }
                    result.append(character);
                }
            }
        }
        return result.toString();
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
