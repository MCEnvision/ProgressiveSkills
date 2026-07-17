package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.id.DefinitionKind;
import com.envisione.progressiveskills.common.id.DefinitionKinds;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Deterministic bounded codec for sanitized definition presentation DTOs. */
public final class DefinitionProjectionCodec {
    private static final int FORMAT_VERSION = 4;

    private DefinitionProjectionCodec() {
    }

    public static byte[] encode(DefinitionProjection projection) {
        return BoundedNetworkCodec.encode(output -> write(output, projection),
                NetworkLimits.MAX_DEFINITION_BYTES, "definition projection");
    }

    public static DefinitionProjection decode(byte[] encoded) {
        try (DataInputStream input = BoundedNetworkCodec.input(
                encoded, NetworkLimits.MAX_DEFINITION_BYTES, "definition projection")) {
            if (input.readInt() != FORMAT_VERSION) {
                throw new IOException("Unsupported definition projection version");
            }
            int count = readCount(input, NetworkLimits.MAX_DEFINITIONS, "definition");
            var definitions = new TreeMap<DefinitionKey, DefinitionProjection.Entry>();
            for (int index = 0; index < count; index++) {
                DefinitionKind kind = definitionKind(
                        readId(input),
                        BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES)
                );
                DefinitionKey key = new DefinitionKey(kind, readId(input));
                Optional<DefinitionProjection.Text> display = readOptionalText(input);
                Optional<DefinitionProjection.Text> description = readOptionalText(input);
                Optional<DefinitionProjection.Icon> icon = input.readBoolean()
                        ? Optional.of(readIcon(input)) : Optional.empty();
                int aliasCount = readCount(input, NetworkLimits.MAX_ALIASES_PER_DEFINITION, "search alias");
                var aliases = new ArrayList<String>(aliasCount);
                for (int alias = 0; alias < aliasCount; alias++) {
                    aliases.add(BoundedNetworkCodec.readString(input, NetworkLimits.MAX_TEXT_BYTES));
                }
                Optional<DefinitionProjection.TreeView> tree = input.readBoolean()
                        ? Optional.of(readTree(input)) : Optional.empty();
                Optional<DefinitionProjection.ClassSlotView> classSlot = input.readBoolean()
                        ? Optional.of(readClassSlot(input)) : Optional.empty();
                Optional<DefinitionProjection.ClassView> classDefinition = input.readBoolean()
                        ? Optional.of(readClass(input)) : Optional.empty();
                Optional<DefinitionProjection.AbilityView> ability = input.readBoolean()
                        ? Optional.of(readAbility(input)) : Optional.empty();
                if (definitions.putIfAbsent(key,
                        new DefinitionProjection.Entry(
                                display, description, icon, aliases, tree, classSlot,
                                classDefinition, ability)) != null) {
                    throw new IOException("Duplicate projected definition " + key);
                }
            }
            int synergyCount = readCount(
                    input, NetworkLimits.MAX_CLASS_SYNERGY_VIEWS, "class synergy");
            var synergies = new TreeMap<ResourceLocation, DefinitionProjection.SynergyView>(
                    ResourceLocation::compareNamespaced);
            for (int index = 0; index < synergyCount; index++) {
                ResourceLocation id = readId(input);
                if (synergies.putIfAbsent(id, readSynergy(input)) != null) {
                    throw new IOException("Duplicate projected class synergy " + id);
                }
            }
            BoundedNetworkCodec.requireFullyRead(input, "definition projection");
            return new DefinitionProjection(definitions, synergies);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid definition projection: " + exception.getMessage(), exception);
        }
    }

    private static void write(DataOutputStream output, DefinitionProjection projection) throws IOException {
        output.writeInt(FORMAT_VERSION);
        output.writeInt(projection.definitions().size());
        for (Map.Entry<DefinitionKey, DefinitionProjection.Entry> definition
                : projection.definitions().entrySet()) {
            writeId(output, definition.getKey().kind().id());
            BoundedNetworkCodec.writeString(output, definition.getKey().kind().sourceDirectory(),
                    NetworkLimits.MAX_KEY_BYTES);
            writeId(output, definition.getKey().id());
            writeOptionalText(output, definition.getValue().display());
            writeOptionalText(output, definition.getValue().description());
            output.writeBoolean(definition.getValue().icon().isPresent());
            if (definition.getValue().icon().isPresent()) {
                writeIcon(output, definition.getValue().icon().orElseThrow());
            }
            output.writeInt(definition.getValue().searchAliases().size());
            for (String alias : definition.getValue().searchAliases()) {
                BoundedNetworkCodec.writeString(output, alias, NetworkLimits.MAX_TEXT_BYTES);
            }
            output.writeBoolean(definition.getValue().tree().isPresent());
            if (definition.getValue().tree().isPresent()) {
                writeTree(output, definition.getValue().tree().orElseThrow());
            }
            output.writeBoolean(definition.getValue().classSlot().isPresent());
            if (definition.getValue().classSlot().isPresent()) {
                writeClassSlot(output, definition.getValue().classSlot().orElseThrow());
            }
            output.writeBoolean(definition.getValue().classDefinition().isPresent());
            if (definition.getValue().classDefinition().isPresent()) {
                writeClass(output, definition.getValue().classDefinition().orElseThrow());
            }
            output.writeBoolean(definition.getValue().ability().isPresent());
            if (definition.getValue().ability().isPresent()) {
                writeAbility(output, definition.getValue().ability().orElseThrow());
            }
        }
        output.writeInt(projection.classSynergies().size());
        for (var synergy : projection.classSynergies().entrySet()) {
            writeId(output, synergy.getKey());
            writeSynergy(output, synergy.getValue());
        }
    }

    private static void writeClassSlot(
            DataOutputStream output,
            DefinitionProjection.ClassSlotView slot
    ) throws IOException {
        output.writeInt(slot.capacity());
        BoundedNetworkCodec.writeString(output, slot.swapPolicy(), 32);
    }

    private static DefinitionProjection.ClassSlotView readClassSlot(DataInputStream input) throws IOException {
        return new DefinitionProjection.ClassSlotView(
                input.readInt(), BoundedNetworkCodec.readString(input, 32));
    }

    private static void writeClass(
            DataOutputStream output,
            DefinitionProjection.ClassView classDefinition
    ) throws IOException {
        output.writeBoolean(classDefinition.enabled());
        writeId(output, classDefinition.slotId());
        output.writeInt(classDefinition.slotCost());
        output.writeBoolean(classDefinition.accessRequired());
        writeIds(output, classDefinition.exclusiveTags());
        output.writeInt(classDefinition.minimumSkillLevels().size());
        for (var minimum : classDefinition.minimumSkillLevels().entrySet()) {
            writeId(output, minimum.getKey());
            output.writeInt(minimum.getValue());
        }
        writeIds(output, classDefinition.requiredNodes());
        writeIds(output, classDefinition.requiredClasses());
        writeOptionalCost(output, classDefinition.selectionCost());
        output.writeBoolean(classDefinition.respecAllowed());
        writeOptionalCost(output, classDefinition.respecCost());
        output.writeInt(classDefinition.starterKit().size());
        for (DefinitionProjection.StarterItemView item : classDefinition.starterKit()) {
            writeId(output, item.item());
            output.writeInt(item.count());
        }
        writeGrants(output, classDefinition.grants());
    }

    private static DefinitionProjection.ClassView readClass(DataInputStream input) throws IOException {
        boolean enabled = input.readBoolean();
        ResourceLocation slotId = readId(input);
        int slotCost = input.readInt();
        boolean accessRequired = input.readBoolean();
        List<ResourceLocation> exclusiveTags = readIds(
                input, NetworkLimits.MAX_CLASS_EXCLUSIVE_TAGS, "class exclusive tag");
        int skillCount = readCount(
                input, NetworkLimits.MAX_CLASS_MINIMUM_SKILLS, "class minimum skill");
        var minimumSkills = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (int index = 0; index < skillCount; index++) {
            ResourceLocation skill = readId(input);
            if (minimumSkills.putIfAbsent(skill, input.readInt()) != null) {
                throw new IOException("Duplicate projected class minimum skill");
            }
        }
        List<ResourceLocation> requiredNodes = readIds(
                input, NetworkLimits.MAX_CLASS_PREREQUISITES, "required class node");
        List<ResourceLocation> requiredClasses = readIds(
                input, NetworkLimits.MAX_CLASS_PREREQUISITES, "required class");
        Optional<DefinitionProjection.CurrencyCostView> selectionCost = readOptionalCost(input);
        boolean respecAllowed = input.readBoolean();
        Optional<DefinitionProjection.CurrencyCostView> respecCost = readOptionalCost(input);
        int starterCount = readCount(
                input, NetworkLimits.MAX_CLASS_STARTER_ITEMS, "starter kit item");
        var starterKit = new ArrayList<DefinitionProjection.StarterItemView>(starterCount);
        for (int index = 0; index < starterCount; index++) {
            starterKit.add(new DefinitionProjection.StarterItemView(readId(input), input.readInt()));
        }
        return new DefinitionProjection.ClassView(
                enabled, slotId, slotCost, accessRequired, exclusiveTags, minimumSkills,
                requiredNodes, requiredClasses, selectionCost, respecAllowed,
                respecCost, starterKit, readGrants(input));
    }

    private static void writeAbility(
            DataOutputStream output,
            DefinitionProjection.AbilityView ability
    ) throws IOException {
        output.writeBoolean(ability.enabled());
        BoundedNetworkCodec.writeString(output, ability.kind(), 32);
        output.writeBoolean(ability.slotAllowed());
        output.writeBoolean(ability.defaultOn());
        output.writeInt(ability.persistentEffects().size());
        for (DefinitionProjection.AbilityEffectView effect : ability.persistentEffects()) {
            writeId(output, effect.id());
            BoundedNetworkCodec.writeString(output, effect.type(), 32);
            writeId(output, effect.target());
            BoundedNetworkCodec.writeString(output, effect.operation(), 32);
            BoundedNetworkCodec.writeString(output, effect.resolver(), 32);
            output.writeLong(effect.value());
        }
        output.writeInt(ability.costs().size());
        for (DefinitionProjection.AbilityCostView cost : ability.costs()) {
            writeId(output, cost.id());
            BoundedNetworkCodec.writeString(output, cost.type(), 32);
            output.writeBoolean(cost.currency().isPresent());
            if (cost.currency().isPresent()) {
                writeId(output, cost.currency().orElseThrow());
            }
            output.writeLong(cost.amount());
        }
        BoundedNetworkCodec.writeString(output, ability.targeting().mode(), 32);
        output.writeInt(ability.targeting().range());
        output.writeBoolean(ability.targeting().lineOfSight());
        writeId(output, ability.cooldownGroup());
        output.writeInt(ability.cooldownTicks());
        output.writeInt(ability.maximumCharges());
        output.writeInt(ability.rechargeTicks());
        output.writeInt(ability.actions().size());
        for (DefinitionProjection.AbilityActionView action : ability.actions()) {
            writeId(output, action.id());
            BoundedNetworkCodec.writeString(output, action.type(), 32);
            writeOptionalText(output, action.message());
            output.writeBoolean(action.target().isPresent());
            if (action.target().isPresent()) {
                writeId(output, action.target().orElseThrow());
            }
            output.writeLong(action.value());
            output.writeInt(action.durationTicks());
            output.writeBoolean(action.ambient());
            output.writeBoolean(action.showParticles());
            output.writeBoolean(action.showIcon());
        }
    }

    private static DefinitionProjection.AbilityView readAbility(DataInputStream input) throws IOException {
        boolean enabled = input.readBoolean();
        String kind = BoundedNetworkCodec.readString(input, 32);
        boolean slotAllowed = input.readBoolean();
        boolean defaultOn = input.readBoolean();
        int effectCount = readCount(
                input, NetworkLimits.MAX_ABILITY_ACTION_SUMMARIES, "ability persistent effect");
        var effects = new ArrayList<DefinitionProjection.AbilityEffectView>(effectCount);
        for (int index = 0; index < effectCount; index++) {
            effects.add(new DefinitionProjection.AbilityEffectView(
                    readId(input), BoundedNetworkCodec.readString(input, 32), readId(input),
                    BoundedNetworkCodec.readString(input, 32),
                    BoundedNetworkCodec.readString(input, 32), input.readLong()));
        }
        int costCount = readCount(input, NetworkLimits.MAX_ABILITY_COST_SUMMARIES, "ability cost");
        var costs = new ArrayList<DefinitionProjection.AbilityCostView>(costCount);
        for (int index = 0; index < costCount; index++) {
            ResourceLocation id = readId(input);
            String type = BoundedNetworkCodec.readString(input, 32);
            Optional<ResourceLocation> currency = input.readBoolean()
                    ? Optional.of(readId(input)) : Optional.empty();
            costs.add(new DefinitionProjection.AbilityCostView(
                    id, type, currency, input.readLong()));
        }
        var targeting = new DefinitionProjection.AbilityTargetView(
                BoundedNetworkCodec.readString(input, 32), input.readInt(), input.readBoolean());
        ResourceLocation cooldownGroup = readId(input);
        int cooldownTicks = input.readInt();
        int maximumCharges = input.readInt();
        int rechargeTicks = input.readInt();
        int actionCount = readCount(
                input, NetworkLimits.MAX_ABILITY_ACTION_SUMMARIES, "ability action");
        var actions = new ArrayList<DefinitionProjection.AbilityActionView>(actionCount);
        for (int index = 0; index < actionCount; index++) {
            ResourceLocation id = readId(input);
            String type = BoundedNetworkCodec.readString(input, 32);
            Optional<DefinitionProjection.Text> message = readOptionalText(input);
            Optional<ResourceLocation> target = input.readBoolean()
                    ? Optional.of(readId(input)) : Optional.empty();
            actions.add(new DefinitionProjection.AbilityActionView(
                    id, type, message, target, input.readLong(), input.readInt(),
                    input.readBoolean(), input.readBoolean(), input.readBoolean()));
        }
        return new DefinitionProjection.AbilityView(
                enabled, kind, slotAllowed, defaultOn, effects, costs, targeting,
                cooldownGroup, cooldownTicks, maximumCharges, rechargeTicks, actions);
    }

    private static void writeSynergy(
            DataOutputStream output,
            DefinitionProjection.SynergyView synergy
    ) throws IOException {
        output.writeBoolean(synergy.enabled());
        writeOptionalText(output, synergy.display());
        writeOptionalText(output, synergy.description());
        output.writeBoolean(synergy.icon().isPresent());
        if (synergy.icon().isPresent()) {
            writeIcon(output, synergy.icon().orElseThrow());
        }
        output.writeInt(synergy.searchAliases().size());
        for (String alias : synergy.searchAliases()) {
            BoundedNetworkCodec.writeString(output, alias, NetworkLimits.MAX_TEXT_BYTES);
        }
        writeIds(output, synergy.requiredClasses());
        writeGrants(output, synergy.grants());
    }

    private static DefinitionProjection.SynergyView readSynergy(DataInputStream input) throws IOException {
        boolean enabled = input.readBoolean();
        Optional<DefinitionProjection.Text> display = readOptionalText(input);
        Optional<DefinitionProjection.Text> description = readOptionalText(input);
        Optional<DefinitionProjection.Icon> icon = input.readBoolean()
                ? Optional.of(readIcon(input)) : Optional.empty();
        int aliasCount = readCount(input, NetworkLimits.MAX_ALIASES_PER_DEFINITION, "synergy alias");
        var aliases = new ArrayList<String>(aliasCount);
        for (int index = 0; index < aliasCount; index++) {
            aliases.add(BoundedNetworkCodec.readString(input, NetworkLimits.MAX_TEXT_BYTES));
        }
        List<ResourceLocation> requiredClasses = readIds(
                input, NetworkLimits.MAX_CLASS_SYNERGY_CLASSES, "synergy required class");
        return new DefinitionProjection.SynergyView(
                enabled, display, description, icon, aliases, requiredClasses, readGrants(input));
    }

    private static void writeOptionalCost(
            DataOutputStream output,
            Optional<DefinitionProjection.CurrencyCostView> cost
    ) throws IOException {
        output.writeBoolean(cost.isPresent());
        if (cost.isPresent()) {
            writeId(output, cost.orElseThrow().currency());
            output.writeLong(cost.orElseThrow().amount());
        }
    }

    private static Optional<DefinitionProjection.CurrencyCostView> readOptionalCost(
            DataInputStream input
    ) throws IOException {
        return input.readBoolean() ? Optional.of(new DefinitionProjection.CurrencyCostView(
                readId(input), input.readLong())) : Optional.empty();
    }

    private static void writeGrants(
            DataOutputStream output,
            List<DefinitionProjection.GrantSummary> grants
    ) throws IOException {
        output.writeInt(grants.size());
        for (DefinitionProjection.GrantSummary grant : grants) {
            BoundedNetworkCodec.writeString(output, grant.type(), 64);
            writeId(output, grant.target());
            BoundedNetworkCodec.writeString(output, grant.operation(), 64);
            BoundedNetworkCodec.writeString(output, grant.resolver(), 32);
            output.writeLong(grant.value());
        }
    }

    private static List<DefinitionProjection.GrantSummary> readGrants(
            DataInputStream input
    ) throws IOException {
        int count = readCount(
                input, NetworkLimits.MAX_CLASS_GRANT_SUMMARIES, "class grant summary");
        var grants = new ArrayList<DefinitionProjection.GrantSummary>(count);
        for (int index = 0; index < count; index++) {
            grants.add(new DefinitionProjection.GrantSummary(
                    BoundedNetworkCodec.readString(input, 64), readId(input),
                    BoundedNetworkCodec.readString(input, 64),
                    BoundedNetworkCodec.readString(input, 32), input.readLong()));
        }
        return grants;
    }

    private static void writeTree(
            DataOutputStream output,
            DefinitionProjection.TreeView tree
    ) throws IOException {
        output.writeBoolean(tree.enabled());
        BoundedNetworkCodec.writeString(output, tree.scope(), 32);
        output.writeBoolean(tree.boundSkill().isPresent());
        if (tree.boundSkill().isPresent()) {
            writeId(output, tree.boundSkill().orElseThrow());
        }
        writeId(output, tree.currency());
        output.writeLong(tree.currencyMinimum());
        output.writeLong(tree.currencyInitial());
        output.writeInt(tree.nodes().size());
        for (DefinitionProjection.NodeView node : tree.nodes()) {
            writeNode(output, node);
        }
    }

    private static DefinitionProjection.TreeView readTree(DataInputStream input) throws IOException {
        boolean enabled = input.readBoolean();
        String scope = BoundedNetworkCodec.readString(input, 32);
        Optional<ResourceLocation> boundSkill = input.readBoolean()
                ? Optional.of(readId(input)) : Optional.empty();
        ResourceLocation currency = readId(input);
        long currencyMinimum = input.readLong();
        long currencyInitial = input.readLong();
        int count = readCount(input, NetworkLimits.MAX_TREE_NODES_PER_VIEW, "tree node");
        if (count == 0) {
            throw new IOException("Projected tree requires a node");
        }
        var nodes = new ArrayList<DefinitionProjection.NodeView>(count);
        for (int index = 0; index < count; index++) {
            nodes.add(readNode(input));
        }
        return new DefinitionProjection.TreeView(
                enabled, scope, boundSkill, currency, currencyMinimum, currencyInitial, nodes);
    }

    private static void writeNode(
            DataOutputStream output,
            DefinitionProjection.NodeView node
    ) throws IOException {
        writeId(output, node.id());
        writeText(output, node.display());
        writeOptionalText(output, node.description());
        writeIcon(output, node.icon());
        output.writeInt(node.searchAliases().size());
        for (String alias : node.searchAliases()) {
            BoundedNetworkCodec.writeString(output, alias, NetworkLimits.MAX_TEXT_BYTES);
        }
        output.writeLong(node.cost());
        output.writeInt(node.row());
        output.writeInt(node.column());
        writeIds(output, node.requires());
        writeIds(output, node.requiresAny());
        output.writeInt(node.minimumSkillLevels().size());
        for (var entry : node.minimumSkillLevels().entrySet()) {
            writeId(output, entry.getKey());
            output.writeInt(entry.getValue());
        }
    }

    private static DefinitionProjection.NodeView readNode(DataInputStream input) throws IOException {
        ResourceLocation id = readId(input);
        DefinitionProjection.Text display = readText(input);
        Optional<DefinitionProjection.Text> description = readOptionalText(input);
        DefinitionProjection.Icon icon = readIcon(input);
        int aliasCount = readCount(input, NetworkLimits.MAX_ALIASES_PER_DEFINITION, "node alias");
        var aliases = new ArrayList<String>(aliasCount);
        for (int index = 0; index < aliasCount; index++) {
            aliases.add(BoundedNetworkCodec.readString(input, NetworkLimits.MAX_TEXT_BYTES));
        }
        long cost = input.readLong();
        int row = input.readInt();
        int column = input.readInt();
        List<ResourceLocation> requires = readIds(
                input, NetworkLimits.MAX_TREE_PREREQUISITES, "tree prerequisite");
        List<ResourceLocation> requiresAny = readIds(
                input, NetworkLimits.MAX_TREE_PREREQUISITES, "tree alternative prerequisite");
        if (requires.size() + requiresAny.size() > NetworkLimits.MAX_TREE_PREREQUISITES) {
            throw new IOException("Projected tree prerequisite count exceeds capacity");
        }
        int minimumCount = readCount(input, NetworkLimits.MAX_TREE_MINIMUM_SKILLS, "minimum skill");
        var minimumLevels = new TreeMap<ResourceLocation, Integer>(ResourceLocation::compareNamespaced);
        for (int index = 0; index < minimumCount; index++) {
            ResourceLocation skill = readId(input);
            if (minimumLevels.putIfAbsent(skill, input.readInt()) != null) {
                throw new IOException("Duplicate projected tree minimum skill");
            }
        }
        return new DefinitionProjection.NodeView(
                id, display, description, icon, aliases, cost, row, column,
                requires, requiresAny, minimumLevels
        );
    }

    private static void writeIds(DataOutputStream output, List<ResourceLocation> ids) throws IOException {
        output.writeInt(ids.size());
        for (ResourceLocation id : ids) {
            writeId(output, id);
        }
    }

    private static List<ResourceLocation> readIds(
            DataInputStream input,
            int maximum,
            String name
    ) throws IOException {
        int count = readCount(input, maximum, name);
        var ids = new ArrayList<ResourceLocation>(count);
        for (int index = 0; index < count; index++) {
            ids.add(readId(input));
        }
        return ids;
    }

    private static void writeOptionalText(
            DataOutputStream output,
            Optional<DefinitionProjection.Text> text
    ) throws IOException {
        output.writeBoolean(text.isPresent());
        if (text.isPresent()) {
            writeText(output, text.orElseThrow());
        }
    }

    private static Optional<DefinitionProjection.Text> readOptionalText(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(readText(input)) : Optional.empty();
    }

    private static void writeText(DataOutputStream output, DefinitionProjection.Text text) throws IOException {
        output.writeBoolean(text.localizationKey().isPresent());
        if (text.localizationKey().isPresent()) {
            BoundedNetworkCodec.writeString(output, text.localizationKey().orElseThrow(),
                    NetworkLimits.MAX_KEY_BYTES);
        }
        BoundedNetworkCodec.writeString(output, text.fallback(), NetworkLimits.MAX_TEXT_BYTES);
    }

    private static DefinitionProjection.Text readText(DataInputStream input) throws IOException {
        Optional<String> key = input.readBoolean()
                ? Optional.of(BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES))
                : Optional.empty();
        return new DefinitionProjection.Text(
                key,
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_TEXT_BYTES)
        );
    }

    private static void writeIcon(DataOutputStream output, DefinitionProjection.Icon icon) throws IOException {
        BoundedNetworkCodec.writeString(output, icon.kind(), 64);
        output.writeInt(icon.references().size());
        for (ResourceLocation reference : icon.references()) {
            writeId(output, reference);
        }
        writeId(output, icon.fallback());
        writeText(output, icon.altText());
        writeText(output, icon.narration());
    }

    private static DefinitionProjection.Icon readIcon(DataInputStream input) throws IOException {
        String kind = BoundedNetworkCodec.readString(input, 64);
        int references = readCount(input, 16, "icon reference");
        if (references == 0) {
            throw new IOException("Projected icon requires a reference");
        }
        var ids = new ArrayList<ResourceLocation>(references);
        for (int index = 0; index < references; index++) {
            ids.add(readId(input));
        }
        return new DefinitionProjection.Icon(
                kind,
                ids,
                readId(input),
                readText(input),
                readText(input)
        );
    }

    private static void writeId(DataOutputStream output, ResourceLocation value) throws IOException {
        BoundedNetworkCodec.writeString(output, value.toString(), NetworkLimits.MAX_KEY_BYTES);
    }

    private static ResourceLocation readId(DataInputStream input) throws IOException {
        ResourceLocation id = ResourceLocation.tryParse(
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES));
        if (id == null) {
            throw new IOException("Invalid projected resource location");
        }
        return id;
    }

    private static DefinitionKind definitionKind(ResourceLocation id, String directory) {
        return DefinitionKinds.all().stream().filter(kind -> kind.id().equals(id)).findFirst()
                .orElseGet(() -> new DefinitionKind(id, directory));
    }

    private static int readCount(DataInputStream input, int maximum, String name) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IOException(name + " count exceeds " + maximum);
        }
        return count;
    }
}
