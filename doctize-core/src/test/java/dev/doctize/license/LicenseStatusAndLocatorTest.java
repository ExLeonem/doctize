package dev.doctize.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseStatusAndLocatorTest {

    @TempDir
    Path directory;

    private final TestLicenses licenses = new TestLicenses();

    private Optional<LicenseLocator.Found> locate(Path file, String key, Map<String, String> environment) {
        return LicenseLocator.locate(file, key, directory.resolve("project"), environment, directory.resolve("home"));
    }

    private void write(String relative, String text) throws IOException {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void withoutAnyLicenseNothingIsFound() {
        assertThat(locate(null, null, Map.of())).isEmpty();
    }

    @Test
    void anExplicitKeyBeatsEverythingElse() throws IOException {
        write("project/doctize.license", "file");

        Optional<LicenseLocator.Found> found = locate(directory.resolve("project/doctize.license"), "the key", Map.of("DOCTIZE_LICENSE", "env"));

        assertThat(found).get().extracting(LicenseLocator.Found::text).isEqualTo("the key");
    }

    @Test
    void anExplicitFileBeatsTheEnvironmentAndTheDefaultFiles() throws IOException {
        write("explicit.license", "explicit");
        write("project/doctize.license", "project");

        assertThat(locate(directory.resolve("explicit.license"), null, Map.of("DOCTIZE_LICENSE", "env"))).get()
                .extracting(LicenseLocator.Found::text).isEqualTo("explicit");
    }

    @Test
    void theEnvironmentBeatsTheDefaultFiles() throws IOException {
        write("project/doctize.license", "project");

        assertThat(locate(null, null, Map.of("DOCTIZE_LICENSE", "env"))).get().extracting(LicenseLocator.Found::text).isEqualTo("env");
    }

    @Test
    void theFileNextToTheConfigurationBeatsTheOneInTheHomeDirectory() throws IOException {
        write("project/doctize.license", "project");
        write("home/.doctize/license", "home");

        assertThat(locate(null, null, Map.of())).get().extracting(LicenseLocator.Found::text).isEqualTo("project");
    }

    @Test
    void theHomeDirectoryIsTheLastPlaceToLook() throws IOException {
        write("home/.doctize/license", "home");

        assertThat(locate(null, null, Map.of())).get().extracting(LicenseLocator.Found::text).isEqualTo("home");
    }

    @Test
    void aBlankEnvironmentVariableIsNoLicense() {
        assertThat(locate(null, null, Map.of("DOCTIZE_LICENSE", "  "))).isEmpty();
    }

    @Test
    void aFileThatWasNamedButDoesNotExistIsAnError() {
        assertThatThrownBy(() -> locate(directory.resolve("missing.license"), null, Map.of()))
                .isInstanceOf(LicenseException.class).hasMessageContaining("missing.license").hasMessageContaining("does not exist");
    }

    private LicenseStatus status(Optional<LicenseLocator.Found> found) {
        return LicenseStatus.of(found, licenses.verifier());
    }

    private Optional<LicenseLocator.Found> found(String token) {
        return Optional.of(new LicenseLocator.Found(token, "the file license.txt"));
    }

    @Test
    void aValidLicenseThatIncludesTheFeatureAllowsIt() {
        LicenseStatus status = status(found(licenses.sign(TestLicenses.license(Set.of(Feature.ARCHITECTURE), null))));

        assertThat(status.denialFor(Feature.ARCHITECTURE)).isEmpty();
        assertThat(status.license()).get().extracting(License::licensee).isEqualTo("Acme Corp");
    }

    @Test
    void aValidLicenseWithoutTheFeatureSaysSo() {
        LicenseStatus status = status(found(licenses.sign(TestLicenses.license(Set.of(), null))));

        assertThat(status.denialFor(Feature.ARCHITECTURE)).get().asString()
                .contains("Acme Corp", "does not include the architecture map");
    }

    @Test
    void noLicenseSaysWhereToPutOne() {
        assertThat(status(Optional.empty()).denialFor(Feature.ARCHITECTURE)).get().asString()
                .contains("needs a Doctize license", "--license", "DOCTIZE_LICENSE", "doctize.license");
    }

    @Test
    void anExpiredLicenseSaysWhenItRanOutAndWhereItCameFrom() {
        LicenseStatus status = status(found(licenses.sign(TestLicenses.license(Set.of(Feature.ARCHITECTURE), LocalDate.of(2026, 1, 31)))));

        assertThat(status.license()).isEmpty();
        assertThat(status.denialFor(Feature.ARCHITECTURE)).get().asString()
                .contains("license.txt", "expired on 2026-01-31");
    }

    @Test
    void aForgedLicenseIsNotUsable() {
        LicenseStatus status = status(found(TestLicenses.signedByAStranger(TestLicenses.license(Set.of(Feature.ARCHITECTURE), null))));

        assertThat(status.denialFor(Feature.ARCHITECTURE)).isPresent();
        assertThat(status.license()).isEmpty();
    }

    @Test
    void theSummaryNamesTheLicenseeTheDateAndTheFeatures() {
        assertThat(TestLicenses.license(Set.of(Feature.ARCHITECTURE), LocalDate.of(2027, 3, 1)).summary())
                .isEqualTo("Acme Corp, valid until 2027-03-01: architecture");
        assertThat(TestLicenses.license(Set.of(), null).summary()).isEqualTo("Acme Corp, does not expire");
    }
}
