package com.envisione.progressiveskills.common.carrier;

import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;

public final class CarrierBehaviorCodec {
    public static final int MAX_ENCODED_BYTES = 65_536;
    private static final int MAGIC = 0x50534342;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_TEXT_BYTES = 512;

    private CarrierBehaviorCodec() {
    }

    public static byte[] encode(CarrierBehaviorSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Carrier behavior snapshot is required");
        }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                write(output, snapshot.definitionId().toString());
                write(output, snapshot.carrier().serializedName());
                output.writeInt(snapshot.behaviorVersion());
                write(output, snapshot.migrationPolicy().serializedName());
                write(output, snapshot.bindPolicy().serializedName());
                write(output, snapshot.deliveryPolicy().serializedName());
                output.writeInt(snapshot.stackSize());
                output.writeInt(snapshot.charges());
                output.writeInt(snapshot.cooldownTicks());
                output.writeInt(snapshot.useActions().size());
                for (CarrierUseAction action : snapshot.useActions()) {
                    writeAction(output, action);
                }
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Carrier behavior encoding exceeds its bound");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in memory carrier encoding failure", exception);
        }
    }

    public static byte[] encodeSnapshot(CarrierBehaviorSnapshot snapshot) {
        return encode(snapshot);
    }

    public static CarrierBehaviorSnapshot decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Carrier behavior bytes are missing or exceed their bound");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Carrier behavior magic is invalid");
            }
            if (input.readInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Carrier behavior format is unsupported");
            }
            ResourceLocation definitionId = ResourceLocation.parse(read(input));
            CarrierKind carrier = CarrierKind.parse(read(input));
            int behaviorVersion = input.readInt();
            CarrierMigrationPolicy migration = CarrierMigrationPolicy.parse(read(input));
            CarrierBindPolicy bind = CarrierBindPolicy.parse(read(input));
            CarrierDeliveryPolicy delivery = CarrierDeliveryPolicy.parse(read(input));
            int stackSize = input.readInt();
            int charges = input.readInt();
            int cooldownTicks = input.readInt();
            int actionCount = input.readInt();
            if (actionCount < 1 || actionCount > CarrierBehaviorSnapshot.MAX_ACTIONS) {
                throw new IllegalArgumentException("Carrier behavior action count exceeds its bound");
            }
            var actions = new ArrayList<CarrierUseAction>(actionCount);
            for (int index = 0; index < actionCount; index++) {
                actions.add(readAction(input));
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Carrier behavior contains trailing data");
            }
            return new CarrierBehaviorSnapshot(
                    definitionId, carrier, behaviorVersion, migration, bind, delivery,
                    stackSize, charges, cooldownTicks, actions
            );
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Carrier behavior is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Carrier behavior is malformed", exception);
        }
    }

    public static CarrierBehaviorSnapshot decodeSnapshot(byte[] encoded) {
        return decode(encoded);
    }

    public static String digest(CarrierBehaviorSnapshot snapshot) {
        return digest(encode(snapshot));
    }

    public static String digest(byte[] encoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static void writeAction(DataOutputStream output, CarrierUseAction action) throws IOException {
        write(output, action.id().toString());
        write(output, action.type().serializedName());
        output.writeInt(action.consume());
        if (action instanceof CarrierSkillXpAction xp) {
            write(output, xp.skill().toString());
            output.writeLong(xp.amountUnits());
        } else if (action instanceof CarrierSkillLevelAction level) {
            write(output, level.skill().toString());
            output.writeInt(level.levels());
        } else if (action instanceof CarrierCurrencyAction currency) {
            write(output, currency.currency().toString());
            output.writeLong(currency.amount());
        } else if (action instanceof CarrierTreeRespecAction tree) {
            write(output, tree.tree().toString());
        } else {
            throw new IllegalArgumentException("Unsupported carrier action " + action.getClass().getName());
        }
    }

    private static CarrierUseAction readAction(DataInputStream input) throws IOException {
        ResourceLocation id = ResourceLocation.parse(read(input));
        CarrierUseActionType type = CarrierUseActionType.parse(read(input));
        int consume = input.readInt();
        return switch (type) {
            case SKILL_XP -> new CarrierSkillXpAction(
                    id, ResourceLocation.parse(read(input)), input.readLong(), consume
            );
            case SKILL_LEVEL -> new CarrierSkillLevelAction(
                    id, ResourceLocation.parse(read(input)), input.readInt(), consume
            );
            case CURRENCY -> new CarrierCurrencyAction(
                    id, ResourceLocation.parse(read(input)), input.readLong(), consume
            );
            case TREE_RESPEC -> new CarrierTreeRespecAction(
                    id, ResourceLocation.parse(read(input)), consume
            );
        };
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Carrier behavior text exceeds its bound");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String read(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES || length > input.available()) {
            throw new IllegalArgumentException("Carrier behavior text length exceeds its bound");
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }
}
