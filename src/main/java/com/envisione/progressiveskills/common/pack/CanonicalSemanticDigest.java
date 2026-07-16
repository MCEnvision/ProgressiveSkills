package com.envisione.progressiveskills.common.pack;

import com.envisione.progressiveskills.common.id.AliasMap;
import com.envisione.progressiveskills.common.id.DefinitionKey;
import com.envisione.progressiveskills.common.ir.CanonicalDefinition;
import com.envisione.progressiveskills.common.ir.CanonicalValue;
import com.envisione.progressiveskills.common.ir.DefinitionHeader;
import com.envisione.progressiveskills.common.ir.DefinitionPresentation;
import com.envisione.progressiveskills.common.ir.SemanticIr;
import com.envisione.progressiveskills.common.presentation.ComponentSpec;
import com.envisione.progressiveskills.common.presentation.IconSpec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/** Stable SHA-256 encoding of semantic IR that excludes source locations and provenance. */
public final class CanonicalSemanticDigest {
    private CanonicalSemanticDigest() {}

    public static String definition(CanonicalDefinition definition) {
        return digest(output -> writeDefinition(output, definition.semanticProjection().header(), definition.fields()));
    }

    public static String ir(SemanticIr ir, Collection<DefinitionKey> disabledDefinitions) {
        return digest(output -> {
            output.writeInt(ir.definitions().size());
            for (var entry : ir.definitions().entrySet()) {
                writeKey(output, entry.getKey());
                writeDefinition(output, entry.getValue().header(), entry.getValue().fields());
            }
            writeAliases(output, ir.aliases());
            var disabled = disabledDefinitions.stream().sorted().toList();
            output.writeInt(disabled.size());
            for (DefinitionKey key : disabled) {
                writeKey(output, key);
            }
        });
    }

    /** Stable semantic encoding of manifest metadata without relying on record {@code toString()}. */
    public static String manifest(PackManifest manifest) {
        return digest(output -> {
            output.writeInt(manifest.schemaVersion());
            writeString(output, manifest.id().toString());
            writeString(output, manifest.namespace());
            writeComponent(output, manifest.name());
            writeString(output, manifest.contentVersion().toString());
            writeString(output, manifest.engine().expression());
            writeStrings(output, manifest.authors());
            writeString(output, manifest.license());
            output.writeInt(manifest.priority());
            writeString(output, manifest.defaultLocale());
            writeOptionalString(output, manifest.defaultTheme().map(Object::toString));
            writeOptionalString(output, manifest.defaultLayout().map(Object::toString));
            writeRequirements(output, manifest.requiredPacks());
            writeRequirements(output, manifest.optionalPacks());
            writeStrings(output, manifest.requiredMods());
            writeStrings(output, manifest.optionalMods());
            writeStrings(output, manifest.incompatibleMods());
            writeOptionalString(output, manifest.homepage());
            writeOptionalString(output, manifest.sourceUrl());
            writeOptionalString(output, manifest.description());
            writeOptionalString(output, manifest.changelogUrl());
            writeStrings(output, manifest.featureFlags());
            writeOptionalString(output, manifest.exportedAssetPackId().map(Object::toString));
            output.writeBoolean(manifest.trustedScripts());
            writeString(output, manifest.policies().missingRequired().name());
            writeString(output, manifest.policies().missingOptional().name());
            writeString(output, manifest.policies().unknownField().name());
            writeString(output, manifest.policies().duplicateId().name());
            writeString(output, manifest.policies().mergeConflict().name());
            writeString(output, manifest.policies().secretProjection().name());
        });
    }

    private static void writeDefinition(
            DataOutputStream output,
            DefinitionHeader header,
            CanonicalValue.ObjectValue fields
    ) throws IOException {
        output.writeInt(header.schemaVersion().value());
        writeKey(output, header.key());
        output.writeBoolean(header.presentation().isPresent());
        if (header.presentation().isPresent()) {
            writePresentation(output, header.presentation().orElseThrow());
        }
        writeValue(output, fields);
    }

    private static void writePresentation(DataOutputStream output, DefinitionPresentation presentation)
            throws IOException {
        writeComponent(output, presentation.display());
        output.writeBoolean(presentation.description().isPresent());
        if (presentation.description().isPresent()) {
            writeComponent(output, presentation.description().orElseThrow());
        }
        writeIcon(output, presentation.icon());
        output.writeInt(presentation.searchAliases().size());
        for (String alias : presentation.searchAliases()) {
            writeString(output, alias);
        }
    }

    private static void writeValue(DataOutputStream output, CanonicalValue value) throws IOException {
        switch (value) {
            case CanonicalValue.BooleanValue booleanValue -> {
                output.writeByte(1);
                output.writeBoolean(booleanValue.value());
            }
            case CanonicalValue.IntegerValue integerValue -> {
                output.writeByte(2);
                output.writeLong(integerValue.value());
            }
            case CanonicalValue.DecimalValue decimalValue -> {
                output.writeByte(3);
                writeString(output, decimalValue.value().toPlainString());
            }
            case CanonicalValue.TextValue textValue -> {
                output.writeByte(4);
                writeString(output, textValue.value());
            }
            case CanonicalValue.IdValue idValue -> {
                output.writeByte(5);
                writeString(output, idValue.value().toString());
            }
            case CanonicalValue.ReferenceValue referenceValue -> {
                output.writeByte(6);
                writeKey(output, referenceValue.value());
            }
            case CanonicalValue.ComponentValue componentValue -> {
                output.writeByte(7);
                writeComponent(output, componentValue.value());
            }
            case CanonicalValue.IconValue iconValue -> {
                output.writeByte(8);
                writeIcon(output, iconValue.value());
            }
            case CanonicalValue.ListValue listValue -> {
                output.writeByte(9);
                output.writeInt(listValue.values().size());
                for (CanonicalValue child : listValue.values()) {
                    writeValue(output, child);
                }
            }
            case CanonicalValue.ObjectValue objectValue -> {
                output.writeByte(10);
                output.writeInt(objectValue.fields().size());
                for (var entry : objectValue.fields().entrySet()) {
                    writeString(output, entry.getKey());
                    writeValue(output, entry.getValue());
                }
            }
        }
    }

    private static void writeComponent(DataOutputStream output, ComponentSpec component) throws IOException {
        output.writeBoolean(component.localizationKey().isPresent());
        if (component.localizationKey().isPresent()) {
            writeString(output, component.localizationKey().orElseThrow());
        }
        writeString(output, component.fallback());
        output.writeInt(component.placeholders().size());
        for (var entry : component.placeholders().entrySet()) {
            writeString(output, entry.getKey());
            writeString(output, entry.getValue().name());
        }
        output.writeBoolean(component.style().color().isPresent());
        if (component.style().color().isPresent()) {
            writeString(output, component.style().color().orElseThrow().value());
        }
        output.writeInt(component.style().decorations().size());
        for (var decoration : component.style().decorations()) {
            writeString(output, decoration.name());
        }
        output.writeBoolean(component.style().font().isPresent());
        if (component.style().font().isPresent()) {
            writeString(output, component.style().font().orElseThrow().toString());
        }
        output.writeInt(component.children().size());
        for (ComponentSpec child : component.children()) {
            writeComponent(output, child);
        }
    }

    private static void writeIcon(DataOutputStream output, IconSpec icon) throws IOException {
        writeString(output, icon.kind().serializedName());
        output.writeInt(icon.references().size());
        for (var reference : icon.references()) {
            writeString(output, reference.toString());
        }
        writeString(output, icon.fallback().toString());
        writeComponent(output, icon.altText());
        output.writeBoolean(icon.narration().isPresent());
        if (icon.narration().isPresent()) {
            writeComponent(output, icon.narration().orElseThrow());
        }
        output.writeBoolean(icon.entityPreviewOptIn());
    }

    private static void writeAliases(DataOutputStream output, AliasMap aliases) throws IOException {
        output.writeInt(aliases.mappings().size());
        for (var entry : aliases.mappings().entrySet()) {
            writeKey(output, entry.getKey());
            writeKey(output, entry.getValue());
        }
    }

    private static void writeRequirements(DataOutputStream output, Collection<PackRequirement> requirements)
            throws IOException {
        output.writeInt(requirements.size());
        for (PackRequirement requirement : requirements) {
            writeString(output, requirement.packId().toString());
            writeOptionalString(output, requirement.version().map(VersionConstraint::expression));
        }
    }

    private static void writeStrings(DataOutputStream output, Collection<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) {
            writeString(output, value);
        }
    }

    private static void writeOptionalString(DataOutputStream output, java.util.Optional<String> value)
            throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeString(output, value.orElseThrow());
        }
    }

    private static void writeKey(DataOutputStream output, DefinitionKey key) throws IOException {
        writeString(output, key.kind().id().toString());
        writeString(output, key.id().toString());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String digest(IoWriter writer) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                writeString(output, "progressiveskills-semantic-ir-v1");
                writer.write(output);
            }
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory digest failure", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
