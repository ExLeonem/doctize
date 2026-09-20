package dev.doctize.license;

import dev.doctize.annotations.Doc;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Checks that a license was issued by the vendor and is still valid, without a network connection.
 *
 * <p>A license is one line of text, {@code <content>.<signature>}, both Base64 (URL-safe, without padding). The content
 * is the JSON of a {@link License}; the signature is an Ed25519 signature of exactly those bytes. Lines that start with
 * {@code #} and blank lines are ignored, so a license file can carry a comment. Whoever holds the private key issues
 * licenses; this class only has the public key, so it cannot.
 */
@Doc(group = "Licensing", order = 62)
public final class LicenseVerifier {

    static final String VENDOR_KEY_RESOURCE = "vendor-public-key.pem";
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final PublicKey key;
    private final Clock clock;

    public LicenseVerifier(PublicKey key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    /** A verifier for licenses issued by the vendor of Doctize, with the public key that ships with this library. */
    public static LicenseVerifier vendor() {
        return new LicenseVerifier(vendorKey(), Clock.systemUTC());
    }

    /**
     * @param text the content of a license file
     * @return the license, if it is authentic and has not expired
     * @throws LicenseException if it is not, saying why
     */
    public License verify(String text) {
        String token = tokenOf(text);
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1 || token.indexOf('.', dot + 1) >= 0) {
            throw new LicenseException("This is not a Doctize license: expected <content>.<signature> on one line.");
        }
        byte[] content;
        byte[] signature;
        try {
            content = DECODER.decode(token.substring(0, dot));
            signature = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw new LicenseException("This is not a Doctize license: it is not valid Base64. Was it cut off or line-wrapped?", e);
        }
        if (!authentic(content, signature)) {
            throw new LicenseException("The signature of the license does not match its content. It was changed, or it was not issued for Doctize.");
        }
        License license = License.fromJson(content);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (license.expiredOn(today)) {
            throw new LicenseException("The license of " + license.licensee() + " expired on " + license.expires() + ".");
        }
        return license;
    }

    private boolean authentic(byte[] content, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(content);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    static String tokenOf(String text) {
        StringBuilder token = new StringBuilder();
        for (String line : text.split("\\R")) {
            String stripped = line.strip();
            if (!stripped.isEmpty() && !stripped.startsWith("#")) {
                token.append(stripped);
            }
        }
        return token.toString();
    }

    /** Reads an Ed25519 public key in PEM format. */
    public static PublicKey readPublicKey(String pem) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").strip());
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Not an Ed25519 public key in PEM format", e);
        }
    }

    private static PublicKey vendorKey() {
        try (InputStream in = LicenseVerifier.class.getResourceAsStream(VENDOR_KEY_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(VENDOR_KEY_RESOURCE + " is missing from the Doctize library");
            }
            return readPublicKey(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
