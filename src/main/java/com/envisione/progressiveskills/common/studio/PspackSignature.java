package com.envisione.progressiveskills.common.studio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

public final class PspackSignature {
    private static final String ALGORITHM = "Ed25519";
    private static final int MAX_DOCUMENT_BYTES = 1_024;

    private PspackSignature() {
    }

    public static SigningIdentity generate() {
        try {
            var pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            return identity(pair.getPrivate().getEncoded(), pair.getPublic().getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Ed25519 is unavailable", exception);
        }
    }

    public static SigningIdentity identity(byte[] privateKey, byte[] publicKey) {
        byte[] privateCopy = Objects.requireNonNull(privateKey, "privateKey").clone();
        byte[] publicCopy = Objects.requireNonNull(publicKey, "publicKey").clone();
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey decodedPrivate = factory.generatePrivate(new PKCS8EncodedKeySpec(privateCopy));
            PublicKey decodedPublic = factory.generatePublic(new X509EncodedKeySpec(publicCopy));
            byte[] probe = "progressiveskills signing identity".getBytes(StandardCharsets.UTF_8);
            byte[] signature = signature(decodedPrivate, probe);
            if (!verify(decodedPublic, probe, signature)) {
                throw new IllegalArgumentException("Progression pack signing keys do not match");
            }
            return new SigningIdentity(privateCopy, publicCopy, fingerprint(publicCopy));
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Progression pack signing identity is invalid", exception);
        }
    }

    public static String sign(SigningIdentity identity, byte[] manifest) {
        Objects.requireNonNull(identity, "identity");
        manifest = Objects.requireNonNull(manifest, "manifest");
        try {
            PrivateKey privateKey = KeyFactory.getInstance(ALGORITHM).generatePrivate(
                    new PKCS8EncodedKeySpec(identity.privateKey()));
            String publicKey = Base64.getEncoder().encodeToString(identity.publicKey());
            String signature = Base64.getEncoder().encodeToString(signature(privateKey, manifest));
            return "algorithm ed25519\n"
                    + "public_key " + publicKey + "\n"
                    + "fingerprint " + identity.fingerprint() + "\n"
                    + "signature " + signature + "\n";
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Progression pack signing identity is invalid", exception);
        }
    }

    public static Signer verify(byte[] manifest, byte[] document) {
        manifest = Objects.requireNonNull(manifest, "manifest");
        document = Objects.requireNonNull(document, "document");
        if (document.length < 1 || document.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Progression pack signature document is invalid");
        }
        String text = new String(document, StandardCharsets.UTF_8);
        String[] lines = text.split("\n", -1);
        if (lines.length != 5 || !lines[0].equals("algorithm ed25519") || !lines[4].isEmpty()
                || !lines[1].startsWith("public_key ")
                || !lines[2].matches("fingerprint [0-9a-f]{64}")
                || !lines[3].startsWith("signature ")) {
            throw new IllegalArgumentException("Progression pack signature document is invalid");
        }
        try {
            byte[] encodedPublic = Base64.getDecoder().decode(lines[1].substring("public_key ".length()));
            byte[] encodedSignature = Base64.getDecoder().decode(lines[3].substring("signature ".length()));
            String expectedFingerprint = fingerprint(encodedPublic);
            String declaredFingerprint = lines[2].substring("fingerprint ".length());
            if (!MessageDigest.isEqual(expectedFingerprint.getBytes(StandardCharsets.US_ASCII),
                    declaredFingerprint.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Progression pack signer fingerprint is invalid");
            }
            PublicKey publicKey = KeyFactory.getInstance(ALGORITHM).generatePublic(
                    new X509EncodedKeySpec(encodedPublic));
            if (!verify(publicKey, manifest, encodedSignature)) {
                throw new IllegalArgumentException("Progression pack signature verification failed");
            }
            return new Signer(expectedFingerprint, encodedPublic);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Progression pack signature is invalid", exception);
        }
    }

    private static byte[] signature(PrivateKey key, byte[] manifest) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initSign(key);
        signature.update(manifest);
        return signature.sign();
    }

    private static boolean verify(PublicKey key, byte[] manifest, byte[] signed) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initVerify(key);
        signature.update(manifest);
        return signature.verify(signed);
    }

    private static String fingerprint(byte[] publicKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SigningIdentity(byte[] privateKey, byte[] publicKey, String fingerprint) {
        public SigningIdentity {
            privateKey = Objects.requireNonNull(privateKey, "privateKey").clone();
            publicKey = Objects.requireNonNull(publicKey, "publicKey").clone();
            if (!Objects.requireNonNull(fingerprint, "fingerprint").matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Progression pack signer fingerprint is invalid");
            }
        }

        @Override
        public byte[] privateKey() {
            return privateKey.clone();
        }

        @Override
        public byte[] publicKey() {
            return publicKey.clone();
        }
    }

    public record Signer(String fingerprint, byte[] publicKey) {
        public Signer {
            if (!Objects.requireNonNull(fingerprint, "fingerprint").matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Progression pack signer fingerprint is invalid");
            }
            publicKey = Objects.requireNonNull(publicKey, "publicKey").clone();
        }

        @Override
        public byte[] publicKey() {
            return publicKey.clone();
        }
    }
}
