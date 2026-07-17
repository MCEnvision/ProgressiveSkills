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
    private static final int FORMAT_VERSION = 1;

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
                if (definitions.putIfAbsent(key,
                        new DefinitionProjection.Entry(display, description, icon, aliases)) != null) {
                    throw new IOException("Duplicate projected definition " + key);
                }
            }
            BoundedNetworkCodec.requireFullyRead(input, "definition projection");
            return new DefinitionProjection(definitions);
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
        }
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
