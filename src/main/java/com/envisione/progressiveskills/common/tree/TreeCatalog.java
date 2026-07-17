package com.envisione.progressiveskills.common.tree;

import com.envisione.progressiveskills.common.id.DefinitionKinds;
import com.envisione.progressiveskills.common.ir.CanonicalIr;
import com.envisione.progressiveskills.common.network.NetworkLimits;
import com.envisione.progressiveskills.common.skill.SkillCatalog;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class TreeCatalog {
    public static final int MAX_TREES = NetworkLimits.MAX_DEFINITIONS;
    private final Map<ResourceLocation, TreeDefinition> trees;

    private TreeCatalog(Map<ResourceLocation, TreeDefinition> trees) {
        this.trees = Collections.unmodifiableMap(new LinkedHashMap<>(trees));
    }

    public static TreeCatalog from(CanonicalIr ir, SkillCatalog skills) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(skills, "skills");
        var trees = new TreeMap<ResourceLocation, TreeDefinition>(ResourceLocation::compareNamespaced);
        var nodeIds = new HashSet<ResourceLocation>();
        ir.definitions().forEach((key, canonical) -> {
            if (!key.kind().equals(DefinitionKinds.TREE)) {
                return;
            }
            if (trees.size() >= MAX_TREES) {
                throw new IllegalArgumentException("Tree count exceeds " + MAX_TREES);
            }
            TreeDefinition tree = TreeCanonicalCodec.decode(canonical);
            var currency = skills.currency(tree.currency()).orElseThrow(() -> new IllegalArgumentException(
                    "Tree " + tree.id() + " references missing currency " + tree.currency()
            ));
            if (tree.scope() == TreeScope.SKILL && skills.skill(tree.boundSkill().orElseThrow()).isEmpty()) {
                throw new IllegalArgumentException("Tree " + tree.id()
                        + " references missing bound skill " + tree.boundSkill().orElseThrow());
            }
            for (TreeNodeDefinition node : tree.nodes()) {
                if (!nodeIds.add(node.id())) {
                    throw new IllegalArgumentException("Tree node id must be globally unique " + node.id());
                }
                if (node.cost() > currency.maximum()) {
                    throw new IllegalArgumentException("Tree node " + node.id()
                            + " cost exceeds currency maximum " + tree.currency());
                }
                node.minimumSkillLevels().forEach((skillId, minimum) -> {
                    var skill = skills.skill(skillId).orElseThrow(() -> new IllegalArgumentException(
                            "Tree node " + node.id() + " references missing skill " + skillId
                    ));
                    if (minimum > skill.curve().maxLevel()) {
                        throw new IllegalArgumentException("Tree node " + node.id()
                                + " minimum level exceeds skill cap " + skillId);
                    }
                });
            }
            if (trees.putIfAbsent(tree.id(), tree) != null) {
                throw new IllegalArgumentException("Duplicate tree id " + tree.id());
            }
        });
        return new TreeCatalog(trees);
    }

    public Map<ResourceLocation, TreeDefinition> trees() {
        return trees;
    }

    public Optional<TreeDefinition> tree(ResourceLocation id) {
        return Optional.ofNullable(trees.get(id));
    }

    public Optional<TreeNodeDefinition> node(ResourceLocation treeId, ResourceLocation nodeId) {
        TreeDefinition tree = trees.get(treeId);
        return tree == null ? Optional.empty() : Optional.ofNullable(tree.nodesById().get(nodeId));
    }

    public String nodeLineageFingerprint(ResourceLocation treeId, ResourceLocation nodeId) {
        TreeDefinition tree = tree(treeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown tree " + treeId)
        );
        TreeNodeDefinition node = tree.nodesById().get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown tree node " + nodeId);
        }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                write(output, "progressiveskills-tree-node-lineage-v1");
                write(output, tree.id().toString());
                write(output, node.id().toString());
                output.writeInt(node.grants().size());
                for (TreeAttributeGrant grant : node.grants()) {
                    write(output, grant.id().toString());
                    write(output, grant.attribute().toString());
                    write(output, grant.operation().serializedName());
                }
            }
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in memory lineage failure", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
