package com.envisione.progressiveskills.client.screen;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.network.PsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StudioGraphScreen extends ProgressiveScreen {
    private final ResourceLocation draftId;
    private final ResourceLocation definitionId;
    private final String display;
    private final List<Node> nodes = new ArrayList<>();
    private final Set<Edge> edges = new LinkedHashSet<>();
    private long revision;
    private int selected;
    private EditBox nodeName;
    private String status = "Add nodes and connect each selected node to its previous node.";

    public StudioGraphScreen(
            ResourceLocation draftId,
            long revision,
            ResourceLocation definitionId,
            String display
    ) {
        super(Component.literal("Studio Tree Graph"));
        this.draftId = draftId;
        this.revision = revision;
        this.definitionId = definitionId;
        this.display = display.isBlank() ? definitionId.getPath() : display;
        nodes.add(new Node(childId("root"), 0, 0));
    }

    @Override
    protected void init() {
        nodeName = addRenderableWidget(new EditBox(
                font, 10, 30, Math.max(90, width - 220), 20, Component.literal("Node name")));
        nodeName.setHint(Component.literal("New node path"));
        nodeName.setMaxLength(64);
        nodeName.setValue("node" + nodes.size());
        addRenderableWidget(Button.builder(Component.literal("Add"), ignored -> addNode())
                .bounds(width - 204, 30, 46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> deleteNode())
                .bounds(width - 154, 30, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Link"), ignored -> linkPrevious())
                .bounds(width - 94, 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), ignored -> select(1))
                .bounds(width - 50, 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Left"), ignored -> move(-1, 0))
                .bounds(10, 54, 46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Right"), ignored -> move(1, 0))
                .bounds(60, 54, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), ignored -> move(0, -1))
                .bounds(114, 54, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), ignored -> move(0, 1))
                .bounds(158, 54, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Write draft"), ignored -> writeDraft())
                .bounds(width - 190, height - 28, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width - 100, height - 28, 90, 20).build());
        setInitialFocus(nodeName);
    }

    @Override
    public void tick() {
        super.tick();
        PsNetworking.consumeStudioFileResult().ifPresent(result -> {
            if (!result.draftId().equals(draftId)) {
                return;
            }
            status = result.message();
            if (result.success()) {
                revision = result.revision();
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackgroundLayer(graphics, mouseX, mouseY, partialTick);
        graphics.fill(6, 6, width - 6, height - 6, 0xEE151515);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawString(font, Component.literal(status), 10, 80, 0xFFCC66, false);
        drawGraph(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new StudioScreen(
                draftId.toString(), Long.toString(revision), definitionId.toString(), display));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        Node node = nodes.get(selected);
        return Component.literal(title.getString() + ". Selected " + node.id() + ". Column "
                + node.column() + ". Row " + node.row() + ". " + status);
    }

    private void addNode() {
        String path = nodeName.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        if (!path.matches("[a-z0-9_.-]{1,64}")) {
            status = "Node path is invalid.";
            return;
        }
        ResourceLocation id = childId(path);
        if (nodes.stream().anyMatch(node -> node.id().equals(id)) || nodes.size() >= 64) {
            status = "Node already exists or graph capacity is full.";
            return;
        }
        Node previous = nodes.get(selected);
        nodes.add(new Node(id, previous.column() + 1, previous.row()));
        int next = nodes.size() - 1;
        edges.add(new Edge(selected, next));
        selected = next;
        nodeName.setValue("node" + nodes.size());
        status = "Node added and linked.";
    }

    private void deleteNode() {
        if (selected == 0 || nodes.size() == 1) {
            status = "The root node cannot be deleted.";
            return;
        }
        int removed = selected;
        nodes.remove(removed);
        var next = new LinkedHashSet<Edge>();
        for (Edge edge : edges) {
            if (edge.from() == removed || edge.to() == removed) {
                continue;
            }
            next.add(new Edge(
                    edge.from() > removed ? edge.from() - 1 : edge.from(),
                    edge.to() > removed ? edge.to() - 1 : edge.to()));
        }
        edges.clear();
        edges.addAll(next);
        selected = Math.min(selected, nodes.size() - 1);
        status = "Node deleted.";
    }

    private void linkPrevious() {
        if (selected < 1) {
            status = "Select a nonroot node before linking.";
            return;
        }
        Edge edge = new Edge(selected - 1, selected);
        if (!edges.add(edge)) {
            edges.remove(edge);
            status = "Link removed.";
        } else {
            status = "Link added.";
        }
    }

    private void select(int amount) {
        selected = Math.floorMod(selected + amount, nodes.size());
        status = "Selected " + nodes.get(selected).id() + ".";
    }

    private void move(int column, int row) {
        Node current = nodes.get(selected);
        nodes.set(selected, new Node(current.id(),
                Math.clamp(current.column() + column, -16, 16),
                Math.clamp(current.row() + row, -16, 16)));
        status = "Node moved.";
    }

    private void writeDraft() {
        PsNetworking.sendStudioFilePut(
                draftId, revision, "trees/" + definitionId.getPath() + ".json", json());
        status = "Writing revision " + revision + ".";
    }

    private void drawGraph(GuiGraphics graphics) {
        int originX = width / 2;
        int originY = Math.max(112, height / 2);
        int[] xs = new int[nodes.size()];
        int[] ys = new int[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            xs[index] = Math.clamp(originX + node.column() * 86, 10, Math.max(10, width - 82));
            ys[index] = Math.clamp(originY + node.row() * 34, 96, Math.max(96, height - 52));
        }
        for (Edge edge : edges) {
            int color = 0xFF88C8FF;
            graphics.fill(Math.min(xs[edge.from()] + 36, xs[edge.to()] + 36), ys[edge.from()] + 8,
                    Math.max(xs[edge.from()] + 36, xs[edge.to()] + 36) + 1, ys[edge.from()] + 10, color);
            graphics.fill(xs[edge.to()] + 35, Math.min(ys[edge.from()] + 8, ys[edge.to()]),
                    xs[edge.to()] + 37, Math.max(ys[edge.from()] + 10, ys[edge.to()] + 1), color);
        }
        for (int index = 0; index < nodes.size(); index++) {
            int color = index == selected ? 0xFF486A3A : 0xFF26384A;
            graphics.fill(xs[index], ys[index], xs[index] + 72, ys[index] + 18, color);
            String label = nodes.get(index).id().getPath();
            label = label.substring(label.lastIndexOf('/') + 1);
            if (label.length() > 10) {
                label = label.substring(0, 9) + ".";
            }
            graphics.drawString(font, Component.literal(label), xs[index] + 4, ys[index] + 5,
                    0xFFFFFF, false);
        }
    }

    private String json() {
        ResourceLocation currency = PsNetworking.clientSnapshot().activeDefinitions().stream()
                .flatMap(value -> value.definitions().keySet().stream())
                .filter(key -> key.kind().equals(DefinitionKinds.CURRENCY))
                .map(key -> key.id()).findFirst()
                .orElse(ResourceLocation.fromNamespaceAndPath("progressiveskills", "global_points"));
        var text = new StringBuilder();
        text.append("{\n  \"schema_version\": 2,\n  \"tree\": {\n")
                .append("    \"id\": \"").append(escape(definitionId.toString())).append("\",\n")
                .append("    \"display\": {\"fallback\": \"").append(escape(display)).append("\"},\n")
                .append("    \"description\": {\"fallback\": \"Studio graph tree\"},\n")
                .append("    \"icon\": {\"type\": \"item\", \"value\": \"minecraft:knowledge_book\", ")
                .append("\"fallback\": \"minecraft:barrier\", \"alt\": \"")
                .append(escape(display)).append("\"},\n")
                .append("    \"enabled\": true, \"scope\": \"global\", \"currency\": \"")
                .append(currency).append("\", \"dependency_policy\": \"cascade_refund\"\n  },\n")
                .append("  \"nodes\": [\n");
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            int nodeIndex = index;
            List<ResourceLocation> parents = edges.stream().filter(edge -> edge.to() == nodeIndex)
                    .map(edge -> nodes.get(edge.from()).id()).toList();
            text.append("    {\"id\": \"").append(escape(node.id().toString()))
                    .append("\", \"display\": {\"fallback\": \"")
                    .append(escape(node.id().getPath().substring(node.id().getPath().lastIndexOf('/') + 1)))
                    .append("\"}, \"description\": {\"fallback\": \"Studio graph node\"}, ")
                    .append("\"icon\": {\"type\": \"item\", \"value\": \"minecraft:paper\", ")
                    .append("\"fallback\": \"minecraft:barrier\", \"alt\": \"Graph node\"}, ")
                    .append("\"cost\": 1, \"row\": ").append(node.row())
                    .append(", \"col\": ").append(node.column()).append(", \"requires\": [");
            for (int parent = 0; parent < parents.size(); parent++) {
                if (parent > 0) {
                    text.append(',');
                }
                text.append('\"').append(escape(parents.get(parent).toString())).append('\"');
            }
            text.append("], \"requires_any\": [], \"min_level\": {}, \"grants\": []}");
            text.append(index + 1 == nodes.size() ? '\n' : ",\n");
        }
        return text.append("  ]\n}\n").toString();
    }

    private ResourceLocation childId(String child) {
        return ResourceLocation.fromNamespaceAndPath(
                definitionId.getNamespace(), definitionId.getPath() + "/" + child);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Node(ResourceLocation id, int column, int row) {
    }

    private record Edge(int from, int to) {
        private Edge {
            if (from < 0 || to < 0 || from == to) {
                throw new IllegalArgumentException("Studio graph edge is invalid");
            }
        }
    }
}
