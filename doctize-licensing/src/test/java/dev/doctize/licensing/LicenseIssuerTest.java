package dev.doctize.licensing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.license.Feature;
import dev.doctize.license.License;
import dev.doctize.license.LicenseException;
import dev.doctize.license.LicenseVerifier;
import java.security.KeyPair;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class LicenseIssuerTest {

    private final KeyPair keys = LicenseIssuer.generateKeys();

    private License license(LocalDate expires) {
        return new License("abc", "Acme Corp", LocalDate.of(2026, 1, 1), expires, EnumSet.allOf(Feature.class));
    }

    @Test
    void aLicenseThatWasIssuedIsAcceptedByTheVerifierOfThePublicKey() {
        String text = LicenseIssuer.issue(license(null), keys.getPrivate());

        License read = new LicenseVerifier(keys.getPublic(), Clock.systemUTC()).verify(text);

        assertThat(read).isEqualTo(license(null));
    }

    @Test
    void theFileStartsWithCommentsForHumansAndTheLicenseIsOneLine() {
        String text = LicenseIssuer.issue(license(LocalDate.of(2099, 1, 1)), keys.getPrivate());

        assertThat(text.lines().toList()).hasSize(3);
        assertThat(text.lines().limit(2)).allSatisfy(line -> assertThat(line).startsWith("# "));
        assertThat(text).contains("Acme Corp", "valid until 2099-01-01", "architecture");
        assertThat(text.lines().skip(2).findFirst().orElseThrow()).matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    @Test
    void anotherKeyDoesNotAcceptTheLicense() {
        String text = LicenseIssuer.issue(license(null), keys.getPrivate());

        assertThatThrownBy(() -> new LicenseVerifier(LicenseIssuer.generateKeys().getPublic(), Clock.systemUTC()).verify(text))
                .isInstanceOf(LicenseException.class).hasMessageContaining("signature");
    }

    @Test
    void keysSurviveAWriteAndReadInPemFormat() {
        String publicPem = LicenseIssuer.toPem(keys.getPublic());
        String privatePem = LicenseIssuer.toPem(keys.getPrivate());
        String text = LicenseIssuer.issue(license(null), LicenseIssuer.readPrivateKey(privatePem));

        assertThat(publicPem).startsWith("-----BEGIN PUBLIC KEY-----\n").endsWith("-----END PUBLIC KEY-----\n");
        assertThat(privatePem).startsWith("-----BEGIN PRIVATE KEY-----\n");
        assertThat(new LicenseVerifier(LicenseVerifier.readPublicKey(publicPem), Clock.systemUTC()).verify(text).licensee()).isEqualTo("Acme Corp");
    }

    @Test
    void aFileThatIsNoPrivateKeyIsRejected() {
        assertThatThrownBy(() -> LicenseIssuer.readPrivateKey("hello")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theKeyThatShipsWithDoctizeIsNotTheOneOfATest() {
        String text = LicenseIssuer.issue(license(null), keys.getPrivate());

        assertThatThrownBy(() -> LicenseVerifier.vendor().verify(text)).isInstanceOf(LicenseException.class);
    }
}
