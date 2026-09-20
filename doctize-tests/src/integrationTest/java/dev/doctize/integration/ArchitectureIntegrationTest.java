package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Doctize;
import dev.doctize.GenerationReport;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.DoctizeConfig;
import dev.doctize.testing.SiteCrawler;
import dev.doctize.testing.TestLicenses;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The architecture map, licensed, on code that was compiled with the real compiler and processor, and on Doctize itself. */
class ArchitectureIntegrationTest {

    @TempDir
    Path work;

    final TestLicenses.Ephemeral vendor = TestLicenses.Ephemeral.create();

    private Doctize.Builder licensed(Doctize.Builder builder) {
        return builder.licenseVerifier(vendor.verifier()).licenseKey(vendor.licenseText()).licenseSearch(Map.of(), work.resolve("home"));
    }

    @Test
    void severalModulesAreOneMapNextToTheirApiReference() throws IOException {
        Path shop = SampleProject.copy("sample-shop", work);
        Path payments = SampleProject.copy("sample-payments", work);
        Path shopClasses = SampleProject.compile(shop);
        Path paymentClasses = SampleProject.compile(payments);
        Path site = work.resolve("site");

        GenerationReport report = licensed(Doctize.builder().configFile(shop.resolve("doctize.yml")).model(shopClasses).model(paymentClasses)
                .architecture().output(site)).build().generate();

        assertThat(report.warnings()).isEmpty();
        String page = Files.readString(site.resolve("architecture/architecture.html"));
        assertThat(page).contains("<pre class=\"mermaid\">flowchart TB", "shop", "payments", "Depends on");
        assertThat(site.resolve("reference/shop.Cart.html")).as("the API reference is unaffected").exists();
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    @Test
    void withoutALicenseTheSameBuildProducesTheSiteWithoutTheMap() {
        Path shop = SampleProject.copy("sample-shop", work);
        Path classes = SampleProject.compile(shop);
        Path site = work.resolve("site");

        GenerationReport report = Doctize.builder().configFile(shop.resolve("doctize.yml")).model(classes).architecture().output(site)
                .licenseVerifier(vendor.verifier()).licenseSearch(Map.of(), work.resolve("home")).build().generate();

        assertThat(site.resolve("architecture")).doesNotExist();
        assertThat(site.resolve("reference/shop.Cart.html")).exists();
        assertThat(report.warnings()).singleElement().asString().contains("architecture diagrams were skipped", "none was found");
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    /** The compiled classes of doctize-core, as the tests see them: a jar or a directory on the class path. */
    private static Path doctizeClasses() throws URISyntaxException {
        return Path.of(Doctize.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static ArchitectureSettings ownRules() {
        return ArchitectureSettings.fromConfig(true, null, null, null, "dev.doctize", null, null, List.of("dev.doctize"), null, null,
                List.of(
                        new ArchitectureSettings.ForbiddenDependency("dev.doctize.license.**", "dev.doctize.**", "Licensing stands alone, so that it can be read and audited on its own."),
                        new ArchitectureSettings.ForbiddenDependency("dev.doctize.model.**", "dev.doctize.**", "The model is the hand-over format between processor and generator."),
                        new ArchitectureSettings.ForbiddenDependency("dev.doctize.text.**", "dev.doctize.**", "Text utilities are the bottom of the code.")),
                false, true, 2, null, null);
    }

    @Test
    void doctizeMeetsItsOwnArchitectureRules() throws Exception {
        Path guides = Files.createDirectories(work.resolve("docs"));
        Files.writeString(guides.resolve("index.md"), "# Doctize\n");

        GenerationReport report = licensed(Doctize.builder().config(new DoctizeConfig(null, null, null, null, null, null, null, ownRules()))
                .guides(guides).classes(doctizeClasses()).output(work.resolve("site"))).build().generate();

        assertThat(report.warnings()).as("a warning here is a broken architecture rule").isEmpty();
        String page = Files.readString(work.resolve("site/architecture/architecture.html"));
        assertThat(page).contains("license", "architecture", "render", "theme", "No dependency breaks a layer or a forbidden dependency.");
        assertThat(page).as("the libraries of the code").contains("com.fasterxml", "org.commonmark", "com.samskivert");
    }

    @Test
    void aRuleThatDoctizeBreaksIsReportedAsAWarning() throws Exception {
        // Rendering does use the site, so this rule is broken, and the warning is what makes `--fail-on-warnings` fail a build.
        ArchitectureSettings broken = ArchitectureSettings.fromConfig(true, null, null, null, "dev.doctize", null, null, List.of("dev.doctize"), null, null,
                List.of(new ArchitectureSettings.ForbiddenDependency("dev.doctize.render.**", "dev.doctize.site.**", "Rendering must not know the site.")),
                false, false, null, null, null);
        Path guides = Files.createDirectories(work.resolve("docs"));
        Files.writeString(guides.resolve("index.md"), "# Doctize\n");

        GenerationReport report = licensed(Doctize.builder().config(new DoctizeConfig(null, null, null, null, null, null, null, broken))
                .guides(guides).classes(doctizeClasses()).output(work.resolve("site"))).build().generate();

        assertThat(report.warnings()).isNotEmpty().allSatisfy(warning -> assertThat(warning)
                .startsWith("Architecture: dev.doctize.render must not depend on dev.doctize.site").contains("Rendering must not know the site."));
        assertThat(Files.readString(work.resolve("site/architecture/architecture.html"))).contains("Rendering must not know the site.");
    }
}
