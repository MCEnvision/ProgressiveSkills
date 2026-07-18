package com.envisione.progressiveskills.common.studio;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PspackSignatureTest {
    @Test
    void signsAndRejectsTamperedManifests() {
        var identity = PspackSignature.generate();
        byte[] manifest = "digest pack/file.json\n".getBytes(StandardCharsets.UTF_8);
        byte[] signature = PspackSignature.sign(identity, manifest).getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> PspackSignature.verify(manifest, signature));
        assertThrows(IllegalArgumentException.class, () -> PspackSignature.verify(
                "changed\n".getBytes(StandardCharsets.UTF_8), signature));

        var otherWorld = PspackSignature.generate();
        var restored = PspackSignature.identity(identity.privateKey(), identity.publicKey());
        byte[] portable = PspackSignature.sign(restored, manifest).getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> PspackSignature.verify(manifest, portable));
        assertThrows(IllegalArgumentException.class, () -> PspackSignature.identity(
                identity.privateKey(), otherWorld.publicKey()));
    }
}
