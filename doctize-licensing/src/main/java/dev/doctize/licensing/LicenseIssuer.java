package dev.doctize.licensing;

import dev.doctize.license.License;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Signs licenses. The counterpart of {@code dev.doctize.license.LicenseVerifier}, which only holds the public key. */
public final class LicenseIssuer {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Encoder PEM = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));

    private LicenseIssuer() {}

    public static KeyPair generateKeys() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The text of a license file: a comment for humans, and the signed license on one line. */
    public static String issue(License license, PrivateKey key) {
        byte[] content = license.toJson();
        return "# Doctize license " + license.id() + " for " + license.licensee() + "\n"
                + "# " + license.summary() + "\n"
                + URL.encodeToString(content) + "." + URL.encodeToString(sign(content, key)) + "\n";
    }

    private static byte[] sign(byte[] content, PrivateKey key) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(content);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toPem(PublicKey key) {
        return pem("PUBLIC KEY", key.getEncoded());
    }

    public static String toPem(PrivateKey key) {
        return pem("PRIVATE KEY", key.getEncoded());
    }

    public static PrivateKey readPrivateKey(String pem) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").strip());
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an Ed25519 private key in PEM format", e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + PEM.encodeToString(der) + "\n-----END " + type + "-----\n";
    }
}
