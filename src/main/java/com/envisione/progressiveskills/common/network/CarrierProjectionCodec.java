package com.envisione.progressiveskills.common.network;

import com.envisione.progressiveskills.common.carrier.CarrierKind;
import com.envisione.progressiveskills.common.id.StableId;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

public final class CarrierProjectionCodec {
    private static final int VERSION = 1;

    private CarrierProjectionCodec() {
    }

    public static byte[] encode(CarrierProjection projection) {
        return BoundedNetworkCodec.encode(output -> write(output, projection),
                NetworkLimits.MAX_CARRIER_PROJECTION_BYTES, "carrier projection");
    }

    public static CarrierProjection decode(byte[] encoded) {
        try (DataInputStream input = BoundedNetworkCodec.input(
                encoded, NetworkLimits.MAX_CARRIER_PROJECTION_BYTES, "carrier projection")) {
            if (input.readInt() != VERSION) {
                throw new IOException("Unsupported carrier projection version");
            }
            Optional<CarrierProjection.HeldCarrier> held = input.readBoolean()
                    ? Optional.of(readHeld(input)) : Optional.empty();
            int claimCount = input.readInt();
            if (claimCount < 0 || claimCount > NetworkLimits.MAX_PENDING_CLAIM_SUMMARIES) {
                throw new IOException("Pending claim projection count exceeds capacity");
            }
            var claims = new ArrayList<CarrierProjection.PendingClaimSummary>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(readClaim(input));
            }
            BoundedNetworkCodec.requireFullyRead(input, "carrier projection");
            return new CarrierProjection(held, claims);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid carrier projection " + exception.getMessage(), exception);
        }
    }

    static void writeEmbedded(DataOutputStream output, CarrierProjection projection) throws IOException {
        byte[] encoded = encode(projection);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static CarrierProjection readEmbedded(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > NetworkLimits.MAX_CARRIER_PROJECTION_BYTES) {
            throw new IOException("Carrier projection length exceeds capacity");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new IOException("Carrier projection is truncated");
        }
        return decode(encoded);
    }

    private static void write(DataOutputStream output, CarrierProjection projection) throws IOException {
        output.writeInt(VERSION);
        output.writeBoolean(projection.held().isPresent());
        if (projection.held().isPresent()) {
            writeHeld(output, projection.held().orElseThrow());
        }
        output.writeInt(projection.pendingClaims().size());
        for (CarrierProjection.PendingClaimSummary claim : projection.pendingClaims()) {
            writeUuid(output, claim.claimId());
            writeId(output, claim.definitionId());
            BoundedNetworkCodec.writeString(
                    output, claim.kind().serializedName(), NetworkLimits.MAX_CARRIER_KIND_BYTES);
            output.writeInt(claim.behaviorVersion());
            output.writeInt(claim.charges());
            output.writeLong(claim.createdAtEpochMillis());
            BoundedNetworkCodec.writeString(
                    output, claim.reason(), NetworkLimits.MAX_CLAIM_REASON_BYTES);
        }
    }

    private static void writeHeld(
            DataOutputStream output,
            CarrierProjection.HeldCarrier held
    ) throws IOException {
        writeId(output, held.definitionId());
        BoundedNetworkCodec.writeString(
                output, held.kind().serializedName(), NetworkLimits.MAX_CARRIER_KIND_BYTES);
        BoundedNetworkCodec.writeString(output, held.behaviorDigest(), 64);
        output.writeInt(held.behaviorVersion());
        output.writeInt(held.charges());
        output.writeBoolean(held.bound());
        output.writeBoolean(held.boundToPlayer());
        output.writeBoolean(held.migrated());
        BoundedNetworkCodec.writeString(
                output, held.status(), NetworkLimits.MAX_CARRIER_STATUS_BYTES);
        BoundedNetworkCodec.writeString(
                output, held.message(), NetworkLimits.MAX_RESYNC_REASON_BYTES);
    }

    private static CarrierProjection.HeldCarrier readHeld(DataInputStream input) throws IOException {
        return new CarrierProjection.HeldCarrier(
                readId(input),
                CarrierKind.parse(BoundedNetworkCodec.readString(
                        input, NetworkLimits.MAX_CARRIER_KIND_BYTES)),
                BoundedNetworkCodec.readString(input, 64),
                input.readInt(),
                input.readInt(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_CARRIER_STATUS_BYTES),
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_RESYNC_REASON_BYTES)
        );
    }

    private static CarrierProjection.PendingClaimSummary readClaim(DataInputStream input) throws IOException {
        return new CarrierProjection.PendingClaimSummary(
                readUuid(input),
                readId(input),
                CarrierKind.parse(BoundedNetworkCodec.readString(
                        input, NetworkLimits.MAX_CARRIER_KIND_BYTES)),
                input.readInt(),
                input.readInt(),
                input.readLong(),
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_CLAIM_REASON_BYTES)
        );
    }

    private static void writeId(DataOutputStream output, ResourceLocation id) throws IOException {
        BoundedNetworkCodec.writeString(output, id.toString(), NetworkLimits.MAX_KEY_BYTES);
    }

    private static ResourceLocation readId(DataInputStream input) throws IOException {
        ResourceLocation id = ResourceLocation.tryParse(
                BoundedNetworkCodec.readString(input, NetworkLimits.MAX_KEY_BYTES));
        if (id == null) {
            throw new IOException("Invalid carrier projection id");
        }
        return StableId.requireValid(id);
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
