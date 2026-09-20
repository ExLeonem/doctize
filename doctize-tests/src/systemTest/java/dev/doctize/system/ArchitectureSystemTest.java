package dev.doctize.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.doctize.testing.Javac;
import dev.doctize.testing.Processes;
import dev.doctize.testing.SiteCrawler;
import dev.doctize.testing.TestLicenses;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The licensed architecture map as a user meets it: the installed command, real license files, and the public key that
 * ships with the product. Licenses that the product accepts can only be issued with the vendor's private key, which is not in
 * the repository, so the tests of the accepted path run where that key is and are skipped elsewhere.
 */
class ArchitectureSystemTest {

    static Path script;
    static Map<String, String> environment;

    @TempDir
    Path work;

    Path project;
    Path classes;

    @BeforeAll
    static void locateTheInstalledCli() {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "the start script is a POSIX shell script");
        script = Path.of(System.getProperty("doctize.cli.home"), "bin", "doctize");
        // A license the machine of whoever runs the tests happens to have must not change the outcome.
        environment = Map.of("JAVA_HOME", System.getProperty("java.home"), "HOME", System.getProperty("java.io.tmpdir") + "/doctize-no-home", "DOCTIZE_LICENSE", "");
    }

    private void aCompiledProject() {
        project = SamplesForSystemTests.copy("sample-shop", work);
        classes = project.resolve("build/classes");
        Javac.compile(project.resolve("java"), classes, ConsumerSystemTest.COMPILE_CLASSPATH, ConsumerSystemTest.PROCESSOR_PATH);
    }

    private Processes.Result doctize(String... arguments) {
        List<String> command = new ArrayList<>(List.of(script.toString()));
        command.addAll(List.of(arguments));
        return Processes.run(command, environment, project, Duration.ofMinutes(2));
    }

    private Path garbageLicense() throws IOException {
        return Files.writeString(work.resolve("garbage.license"), "not a license\n");
    }

    @Test
    void withoutAUsableLicenseTheMapIsSkippedWithAWarningAndTheSiteIsBuilt() throws IOException {
        aCompiledProject();

        Processes.Result result = doctize("generate", "--config", "doctize.yml", "--model", classes.toString(), "--output", "site", "--architecture",
                "--license", garbageLicense().toString());

        assertThat(result.exitCode()).as(result.toString()).isZero();
        assertThat(result.err()).contains("doctize: warning: The architecture map was skipped.", "not a Doctize license");
        assertThat(project.resolve("site/index.html")).exists();
        assertThat(project.resolve("site/architecture")).doesNotExist();
    }

    @Test
    void failOnWarningsMakesTheMissingLicenseFailTheBuild() throws IOException {
        aCompiledProject();

        Processes.Result result = doctize("generate", "--config", "doctize.yml", "--model", classes.toString(), "--output", "site", "--architecture",
                "--license", garbageLicense().toString(), "--fail-on-warnings");

        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    void aLicenseSignedWithAnotherKeyIsRefusedByTheInstalledProduct() throws IOException {
        aCompiledProject();
        Path forged = Files.writeString(work.resolve("forged.license"), TestLicenses.Ephemeral.create().licenseText());

        Processes.Result license = doctize("license", "--license", forged.toString());
        Processes.Result generate = doctize("generate", "--config", "doctize.yml", "--model", classes.toString(), "--output", "site", "--architecture",
                "--license", forged.toString());

        assertThat(license.exitCode()).isEqualTo(1);
        assertThat(license.out()).contains("Not usable:", "signature");
        assertThat(generate.err()).contains("architecture map was skipped");
        assertThat(project.resolve("site/architecture")).doesNotExist();
    }

    @Test
    void anExpiredLicenseOfTheVendorIsRefusedToo() throws IOException {
        PrivateKey key = TestLicenses.vendorKey().orElse(null);
        assumeTrue(key != null, "the private key of the vendor is not available here");
        aCompiledProject();
        Path expired = Files.writeString(work.resolve("expired.license"),
                TestLicenses.issue(key, EnumSet.allOf(dev.doctize.license.Feature.class), java.time.LocalDate.now().minusDays(2)));

        Processes.Result license = doctize("license", "--license", expired.toString());

        assertThat(license.exitCode()).isEqualTo(1);
        assertThat(license.out()).contains("Not usable:", "expired on");
    }

    @Test
    void withALicenseOfTheVendorTheInstalledCliGeneratesTheMap() throws IOException {
        PrivateKey key = TestLicenses.vendorKey().orElse(null);
        assumeTrue(key != null, "the private key of the vendor is not available here");
        aCompiledProject();
        Path license = TestLicenses.writeVendorLicense(work.resolve("vendor.license"), key);

        Processes.Result show = doctize("license", "--license", license.toString());
        Processes.Result generate = doctize("generate", "--config", "doctize.yml", "--model", classes.toString(), "--output", "site", "--architecture",
                "--license", license.toString(), "--fail-on-warnings");

        assertThat(show.exitCode()).as(show.toString()).isZero();
        assertThat(show.out()).contains("Licensed to Test Licensee", "architecture: unlocked");
        assertThat(generate.exitCode()).as(generate.toString()).isZero();
        assertThat(generate.out()).contains("Licensed to Test Licensee");
        assertThat(Files.readString(project.resolve("site/architecture/architecture.html"))).contains("<svg class=\"arch-map\"", "shop");
        assertThat(SiteCrawler.check(project.resolve("site"), "")).isEmpty();
    }

    @Test
    void theLicenseInTheEnvironmentIsFoundByTheInstalledCli() throws IOException {
        PrivateKey key = TestLicenses.vendorKey().orElse(null);
        assumeTrue(key != null, "the private key of the vendor is not available here");
        aCompiledProject();
        String text = Files.readString(TestLicenses.writeVendorLicense(work.resolve("vendor.license"), key));
        Map<String, String> withLicense = new java.util.HashMap<>(environment);
        withLicense.put("DOCTIZE_LICENSE", text);

        Processes.Result result = Processes.run(List.of(script.toString(), "generate", "--config", "doctize.yml", "--classes", classes.toString(),
                "--model", classes.toString(), "--output", "site", "--architecture"), withLicense, project, Duration.ofMinutes(2));

        assertThat(result.exitCode()).as(result.toString()).isZero();
        assertThat(project.resolve("site/architecture/architecture.html")).exists();
    }
}
