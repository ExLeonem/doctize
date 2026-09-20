package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.Doctize;
import dev.doctize.GenerationReport;
import dev.doctize.license.Feature;
import dev.doctize.license.License;
import dev.doctize.license.LicenseException;
import dev.doctize.license.TestLicenses;
import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Placement;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import dev.doctize.model.SourceModelJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The architecture map is a licensed feature: what happens with, and without, a license. */
class ArchitectureLicensingTest {

    @TempDir
    Path work;

    Path classes;
    Path guides;
    Path site;
    final TestLicenses licenses = new TestLicenses();

    @BeforeEach
    void aProjectWithGuides() throws IOException {
        classes = CompiledSources.compile(work.resolve("compiled"), CompiledSources.layeredProject());
        guides = Files.createDirectories(work.resolve("docs"));
        Files.writeString(guides.resolve("index.md"), "# Welcome\n\nHello.\n");
        Files.writeString(guides.resolve("start.md"), "# Start\n\nBegin here.\n");
        site = work.resolve("site");
    }

    private Doctize.Builder builder() {
        // The search does not look at the real environment or home directory of whoever runs the tests.
        return Doctize.builder().baseDirectory(work).guides(guides).classes(classes).output(site)
                .licenseVerifier(licenses.verifier()).licenseSearch(Map.of(), work.resolve("home"));
    }

    private String validLicense() {
        return licenses.sign(TestLicenses.license(Set.of(Feature.ARCHITECTURE), null));
    }

    @Test
    void withALicenseTheMapIsPartOfTheSite() throws IOException {
        GenerationReport report = builder().architecture().licenseKey(validLicense()).build().generate();

        assertThat(site.resolve("architecture/architecture.html")).exists();
        assertThat(Files.readString(site.resolve("architecture/architecture.html"))).contains("<svg class=\"arch-map\"", "<h2 id=\"map\">Map", "api depends on service");
        assertThat(Files.readString(site.resolve("index.html"))).as("the navigation lists it").contains("architecture/architecture.html");
        assertThat(Files.readString(site.resolve("search-index.js"))).as("the tables of the page can be searched").contains("Dependency cycles");
        assertThat(report.license()).get().extracting(License::licensee).isEqualTo("Acme Corp");
        assertThat(report.warnings()).containsExactly("Architecture: dependency cycle between domain, infra.");
    }

    @Test
    void withoutALicenseThereIsNoMapButTheRestOfTheSiteIsGenerated() throws IOException {
        GenerationReport report = builder().architecture().build().generate();

        assertThat(site.resolve("architecture")).doesNotExist();
        assertThat(site.resolve("index.html")).exists();
        assertThat(site.resolve("start.html")).doesNotExist();
        assertThat(report.license()).isEmpty();
        assertThat(report.warnings()).singleElement().asString()
                .startsWith("The architecture map was skipped.").contains("needs a Doctize license", "none was found");
    }

    @Test
    void switchedOffTheMapCostsNothingAndSaysNothing() {
        GenerationReport report = builder().licenseKey(validLicense()).build().generate();

        assertThat(site.resolve("architecture")).doesNotExist();
        assertThat(report.warnings()).isEmpty();
        assertThat(report.license()).as("the license is still reported, so that the CLI can show it").isPresent();
    }

    @Test
    void anExpiredLicenseSkipsTheMapAndSaysWhen() {
        String expired = licenses.sign(TestLicenses.license(Set.of(Feature.ARCHITECTURE), LocalDate.of(2026, 9, 1)));

        GenerationReport report = builder().architecture().licenseKey(expired).build().generate();

        assertThat(site.resolve("architecture")).doesNotExist();
        assertThat(report.warnings()).singleElement().asString().contains("architecture map was skipped", "expired on 2026-09-01");
        assertThat(report.license()).isEmpty();
    }

    @Test
    void aLicenseForOtherFeaturesDoesNotUnlockTheMap() {
        String other = licenses.sign(TestLicenses.license(Set.of(), null));

        GenerationReport report = builder().architecture().licenseKey(other).build().generate();

        assertThat(site.resolve("architecture")).doesNotExist();
        assertThat(report.warnings()).singleElement().asString().contains("does not include the architecture map");
    }

    @Test
    void aForgedLicenseDoesNotUnlockTheMap() {
        String forged = TestLicenses.signedByAStranger(TestLicenses.license(Set.of(Feature.ARCHITECTURE), null));

        GenerationReport report = builder().architecture().licenseKey(forged).build().generate();

        assertThat(site.resolve("architecture")).doesNotExist();
        assertThat(report.warnings()).singleElement().asString().contains("signature");
    }

    @Test
    void theLicenseFileNextToTheConfigurationIsFound() throws IOException {
        Files.writeString(work.resolve("doctize.license"), "# for Acme\n" + validLicense() + "\n");

        GenerationReport report = builder().architecture().build().generate();

        assertThat(site.resolve("architecture/architecture.html")).exists();
        assertThat(report.license()).isPresent();
    }

    @Test
    void theEnvironmentVariableIsFound() {
        GenerationReport report = builder().architecture().licenseSearch(Map.of("DOCTIZE_LICENSE", validLicense()), work.resolve("home")).build().generate();

        assertThat(site.resolve("architecture/architecture.html")).exists();
        assertThat(report.license()).isPresent();
    }

    @Test
    void aLicenseFileGivenExplicitlyIsUsedAndAMissingOneIsAnError() throws IOException {
        Path file = work.resolve("elsewhere.txt");
        Files.writeString(file, validLicense());

        builder().architecture().license(file).build().generate();
        assertThat(site.resolve("architecture/architecture.html")).exists();

        assertThatThrownBy(() -> builder().architecture().license(work.resolve("nope.txt")).build().generate())
                .isInstanceOf(LicenseException.class).hasMessageContaining("nope.txt");
    }

    @Test
    void theConfigurationFileSwitchesOnAndConfiguresTheMap() throws IOException {
        Path config = work.resolve("doctize.yml");
        Files.writeString(config, """
                guides: docs
                architecture:
                  enabled: true
                  title: Structure
                  section: Design
                  order: 3
                  exclude: ["**.infra"]
                  components:
                    - name: Core
                      packages: [com.acme.domain, com.acme.service]
                      description: The business.
                """);

        GenerationReport report = Doctize.builder().configFile(config).classes(classes).output(site)
                .licenseVerifier(licenses.verifier()).licenseKey(validLicense()).build().generate();

        Path page = site.resolve("design/architecture.html");
        assertThat(page).exists();
        String html = Files.readString(page);
        assertThat(html).contains("<title>Structure", "The business.").doesNotContain("infra");
        assertThat(report.warnings()).as("excluding infra removed the cycle").isEmpty();
    }

    @Test
    void theClassDirectoryGivenAsTheModelOfTheApiReferenceIsAnalyzedToo() throws IOException {
        // As on the command line: --model build/classes/java/main, and no --classes.
        Declaration type = new Declaration(ElementKind.CLASS, "Order", "com.acme.domain.Order", "com.acme.domain", "public record Order", List.of(), List.of(), null, List.of());
        SourceModel model = SourceModel.of(List.of(new SourceElement(Role.API, type, "An order.", Placement.none(), List.of(), List.of())));
        Path modelFile = classes.resolve("META-INF/doctize/model.json");
        Files.createDirectories(modelFile.getParent());
        try (var out = Files.newOutputStream(modelFile)) {
            SourceModelJson.write(model, out);
        }

        Doctize.builder().baseDirectory(work).guides(guides).model(classes).output(site).licenseVerifier(licenses.verifier())
                .licenseSearch(Map.of(), work.resolve("home")).architecture().licenseKey(validLicense()).build().generate();

        assertThat(site.resolve("architecture/architecture.html")).exists();
        assertThat(site.resolve("api-reference/com.acme.domain.Order.html")).exists();
    }
}
