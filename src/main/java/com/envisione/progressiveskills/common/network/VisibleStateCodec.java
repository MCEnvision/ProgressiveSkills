package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.transaction.DefinitionRevision;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict codec for full visible state and semantic deltas. */
public final class VisibleStateCodec {
    private static final int FULL_VERSION = 2;
    private static final int DELTA_VERSION = 2;

    private VisibleStateCodec() {
    }

    public static byte[] encode(VisiblePlayerState state) {
        return BoundedNetworkCodec.encode(output -> writeState(output, state),
                NetworkLimits.MAX_STATE_BYTES, "visible player state");
    }

    public static VisiblePlayerState decode(byte[] encoded) {
        try (DataInputStream input = BoundedNetworkCodec.input(
                encoded, NetworkLimits.MAX_STATE_BYTES, "visible player state")) {
            if (input.readInt() != FULL_VERSION) {
                throw new IOException("Unsupported visible-state version");
            }
            VisiblePlayerState result = new VisiblePlayerState(
                    readUuid(input),
                    input.readLong(),
                    input.readLong(),
                    readDefinition(input),
                    input.readLong(),
                    BoundedNetworkCodec.readString(input, 64),
                    readMap(input),
                    readMap(input),
                    readNodeRanks(input),
                    input.readInt(),
                    input.readInt(),
                    input.readBoolean()
            );
            BoundedNetworkCodec.requireFullyRead(input, "visible player state");
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid visible player state: " + exception.getMessage(), exception);
        }
    }

    public static byte[] encodeDelta(StateDelta delta) {
        return BoundedNetworkCodec.encode(output -> writeDelta(output, delta),
                NetworkLimits.MAX_STATE_BYTES, "visible state delta");
    }

    public static StateDelta decodeDelta(byte[] encoded) {
        try (DataInputStream input = BoundedNetworkCodec.input(
                encoded, NetworkLimits.MAX_STATE_BYTES, "visible state delta")) {
            if (input.readInt() != DELTA_VERSION) {
                throw new IOException("Unsupported state-delta version");
            }
            StateDelta result = new StateDelta(
                    readUuid(input),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    readDefinition(input),
                    input.readLong(),
                    BoundedNetworkCodec.readString(input, 64),
                    readMap(input),
                    readSet(input),
                    readMap(input),
                    readSet(input),
                    readNodeRanks(input),
                    readNodeIds(input),
                    input.readInt(),
                    input.readInt(),
                    input.readBoolean(),
                    BoundedNetworkCodec.readString(input, 64)
            );
            BoundedNetworkCodec.requireFullyRead(input, "visible state delta");
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid visible state delta: " + exception.getMessage(), exception);
        }
    }

    public static String digest(VisiblePlayerState state) {
        return BoundedNetworkCodec.digest(encode(state));
    }

    private static void writeState(DataOutputStream output, VisiblePlayerState state) throws IOException {
        output.writeInt(FULL_VERSION);
        writeUuid(output, state.playerId());
        output.writeLong(state.syncRevision());
        output.writeLong(state.stateRevision());
        writeDefinition(output, state.definitionRevision());
        output.writeLong(state.presentationRevision());
        BoundedNetworkCodec.writeString(output, state.presentationDigest(), 64);
        writeMap(output, state.balances());
        writeMap(output, state.effectiveValues());
        writeNodeRanks(output, state.nodeRanks());
        output.writeInt(state.orphanCount());
        output.writeInt(state.operationReceiptCount());
        output.writeBoolean(state.quarantined());
    }

    private static void writeDelta(DataOutputStream output, StateDelta delta) throws IOException {
        output.writeInt(DELTA_VERSION);
        writeUuid(output, delta.playerId());
        output.writeLong(delta.baseSyncRevision());
        output.writeLong(delta.newSyncRevision());
        output.writeLong(delta.newStateRevision());
        writeDefinition(output, delta.definitionRevision());
        output.writeLong(delta.presentationRevision());
        BoundedNetworkCodec.writeString(output, delta.presentationDigest(), 64);
        writeMap(output, delta.changedBalances());
        writeSet(output, delta.removedBalances());
        writeMap(output, delta.changedEffectiveValues());
        writeSet(output, delta.removedEffectiveValues());
        writeNodeRanks(output, delta.changedNodeRanks());
        writeNodeIds(output, delta.removedNodeRanks());
        output.writeInt(delta.orphanCount());
        output.writeInt(delta.operationReceiptCount());
        output.writeBoolean(delta.quarantined());
        BoundedNetworkCodec.writeString(output, delta.resultingStateDigest(), 64);
    }

    private static void writeDefinition(DataOutputStream output, DefinitionRevision revision) throws IOException {
        output.writeLong(revision.generation());
        BoundedNetworkCodec.writeString(output, revision.semanticDigest(), 64);
    }

    private static DefinitionRevision readDefinition(DataInputStream input) throws IOException {
        return new DefinitionRevision(input.readLong(), BoundedNetworkCodec.readString(input, 64));
    }

    private static void writeMap(DataOutputStream output, Map<String, Long> values) throws IOException {
        output.writeInt(values.size());
        for (var entry : values.entrySet()) {
            BoundedNetworkCodec.writeString(output, entry.getKey(), NetworkLimits.MAX_KEY_BYTES);
            output.writeLong(entry.getValue());
        }
    }

    private static Map<String, Long> readMap(DataInputStream input) throws IOException {
        int count = readCount(input);
        var values = new LinkedHashMap<String, Long>();
        for (int index = 0; index < count; index++) {
            String key = BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES);
            if (values.putIfAbsent(key, input.readLong()) != null) {
                throw new IOException("Duplicate visible-state path");
            }
        }
        return values;
    }

    private static void writeSet(DataOutputStream output, Set<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) {
            BoundedNetworkCodec.writeString(output, value, NetworkLimits.MAX_KEY_BYTES);
        }
    }

    private static Set<String> readSet(DataInputStream input) throws IOException {
        int count = readCount(input);
        var values = new LinkedHashSet<String>();
        for (int index = 0; index < count; index++) {
            if (!values.add(BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES))) {
                throw new IOException("Duplicate removed visible-state path");
            }
        }
        return values;
    }

    private static void writeNodeRanks(
            DataOutputStream output,
            Map<ResourceLocation, Integer> ranks
    ) throws IOException {
        output.writeInt(ranks.size());
        for (var entry : ranks.entrySet()) {
            BoundedNetworkCodec.writeString(output, entry.getKey().toString(), NetworkLimits.MAX_KEY_BYTES);
            output.writeInt(entry.getValue());
        }
    }

    private static Map<ResourceLocation, Integer> readNodeRanks(DataInputStream input) throws IOException {
        int count = readCount(input);
        var ranks = new LinkedHashMap<ResourceLocation, Integer>();
        for (int index = 0; index < count; index++) {
            ResourceLocation node = readNodeId(input);
            if (ranks.putIfAbsent(node, input.readInt()) != null) {
                throw new IOException("Duplicate visible node rank");
            }
        }
        return ranks;
    }

    private static void writeNodeIds(
            DataOutputStream output,
            Set<ResourceLocation> nodes
    ) throws IOException {
        output.writeInt(nodes.size());
        for (ResourceLocation node : nodes) {
            BoundedNetworkCodec.writeString(output, node.toString(), NetworkLimits.MAX_KEY_BYTES);
        }
    }

    private static Set<ResourceLocation> readNodeIds(DataInputStream input) throws IOException {
        int count = readCount(input);
        var nodes = new LinkedHashSet<ResourceLocation>();
        for (int index = 0; index < count; index++) {
            if (!nodes.add(readNodeId(input))) {
                throw new IOException("Duplicate removed visible node rank");
            }
        }
        return nodes;
    }

    private static ResourceLocation readNodeId(DataInputStream input) throws IOException {
        ResourceLocation node = ResourceLocation.tryParse(
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES));
        if (node == null) {
            throw new IOException("Invalid visible node id");
        }
        return node;
    }

    private static int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > NetworkLimits.MAX_VISIBLE_VALUES) {
            throw new IOException("Visible-state collection count exceeds capacity");
        }
        return count;
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
