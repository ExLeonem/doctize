package dev.doctize.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicenseVerifierTest {

    private final TestLicenses licenses = new TestLicenses();
    private final LicenseVerifier verifier = licenses.verifier();

    private static License license(LocalDate expires) {
        return TestLicenses.license(Set.of(Feature.ARCHITECTURE), expires);
    }

    @Test
    void aLicenseSignedWithTheRightKeyIsAccepted() {
        License read = verifier.verify(licenses.sign(license(LocalDate.of(2027, 1, 1))));

        assertThat(read.licensee()).isEqualTo("Acme Corp");
        assertThat(read.id()).isEqualTo("L-1");
        assertThat(read.expires()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(read.allows(Feature.ARCHITECTURE)).isTrue();
    }

    @Test
    void aLicenseWithoutAnExpiryNeverRunsOut() {
        assertThat(verifier.verify(licenses.sign(license(null))).expires()).isNull();
    }

    @Test
    void aLicenseIsStillValidOnItsLastDayAndNotAfter() {
        assertThat(verifier.verify(licenses.sign(license(TestLicenses.TODAY))).licensee()).isEqualTo("Acme Corp");
        assertThatThrownBy(() -> verifier.verify(licenses.sign(license(TestLicenses.TODAY.minusDays(1)))))
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("expired on 2026-09-19");
    }

    @Test
    void aLicenseSignedByAnotherKeyIsRefused() {
        String forged = TestLicenses.signedByAStranger(license(null));

        assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(LicenseException.class).hasMessageContaining("signature");
    }

    @Test
    void changingTheContentBreaksTheSignature() {
        String token = licenses.sign(license(LocalDate.of(2027, 1, 1)));
        String signature = token.substring(token.indexOf('.'));
        String extended = new String(license(LocalDate.of(2027, 1, 1)).toJson(), StandardCharsets.UTF_8).replace("2027-01-01", "2099-01-01");
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(extended.getBytes(StandardCharsets.UTF_8)) + signature;

        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(LicenseException.class).hasMessageContaining("signature");
    }

    @Test
    void textThatIsNoLicenseIsRefusedWithAHelpfulMessage() {
        assertThatThrownBy(() -> verifier.verify("hello")).isInstanceOf(LicenseException.class).hasMessageContaining("not a Doctize license");
        assertThatThrownBy(() -> verifier.verify("")).isInstanceOf(LicenseException.class);
        assertThatThrownBy(() -> verifier.verify("a.b.c")).isInstanceOf(LicenseException.class).hasMessageContaining("not a Doctize license");
        assertThatThrownBy(() -> verifier.verify("!!!.???")).isInstanceOf(LicenseException.class).hasMessageContaining("Base64");
    }

    @Test
    void commentsAndBlankLinesInALicenseFileAreIgnored() {
        String file = "# Doctize license for Acme\n\n# valid for a year\n" + licenses.sign(license(null)) + "\n";

        assertThat(verifier.verify(file).licensee()).isEqualTo("Acme Corp");
    }

    @Test
    void aLicenseThatWasWrappedOverSeveralLinesStillWorks() {
        String token = licenses.sign(license(null));
        String wrapped = token.substring(0, 40) + "\n" + token.substring(40, 90) + "\n  " + token.substring(90) + "\n";

        assertThat(verifier.verify(wrapped).licensee()).isEqualTo("Acme Corp");
    }

    @Test
    void aFormatThisVersionDoesNotKnowIsSaidSo() {
        String future = licenses.sign("{\"version\":2,\"id\":\"x\",\"licensee\":\"y\",\"issued\":\"2026-01-01\",\"features\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> verifier.verify(future)).isInstanceOf(LicenseException.class).hasMessageContaining("Update Doctize");
    }

    @Test
    void featuresThisVersionDoesNotKnowAreIgnoredInsteadOfRefusingTheLicense() {
        String newer = licenses.sign(
                "{\"version\":1,\"id\":\"x\",\"licensee\":\"y\",\"issued\":\"2026-01-01\",\"features\":[\"architecture\",\"telepathy\"]}".getBytes(StandardCharsets.UTF_8));

        assertThat(verifier.verify(newer).features()).containsExactly(Feature.ARCHITECTURE);
    }

    @Test
    void aLicenseWithDamagedContentIsRefusedEvenIfItIsSigned() {
        String damaged = licenses.sign("{\"version\":1,\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> verifier.verify(damaged)).isInstanceOf(LicenseException.class).hasMessageContaining("damaged");
    }

    @Test
    void theVendorKeyThatShipsWithTheLibraryIsAValidEd25519Key() {
        LicenseVerifier vendor = LicenseVerifier.vendor();

        assertThatThrownBy(() -> vendor.verify(licenses.sign(license(null))))
                .as("a license of the test key is not one of the vendor")
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void aBrokenPublicKeyIsReported() {
        assertThatThrownBy(() -> LicenseVerifier.readPublicKey("not a key")).isInstanceOf(IllegalStateException.class);
    }
}
