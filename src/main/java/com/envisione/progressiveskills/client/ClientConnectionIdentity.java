package com.envisione.progressiveskills.client;

import com.envisione.progressiveskills.common.network.BoundedNetworkCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Resolves the stable client selected destination without retaining its readable address. */
final class ClientConnectionIdentity {
    private ClientConnectionIdentity() {
    }

    static String current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalServer() || minecraft.hasSingleplayerServer()) {
            return "singleplayer";
        }
        ServerData server = minecraft.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank()) {
            return "";
        }
        String normalizedAddress = server.ip.strip().toLowerCase(Locale.ROOT);
        return "multiplayer:" + BoundedNetworkCodec.digest(
                normalizedAddress.getBytes(StandardCharsets.UTF_8));
    }
}
