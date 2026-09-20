package dev.doctize.licensing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.license.Feature;
import dev.doctize.license.License;
import dev.doctize.license.LicenseVerifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class LicenseToolTest {

    @TempDir
    Path dir;

    private record Result(int exitCode, String out, String err) {}

    private static Result run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true));
            System.setErr(new PrintStream(err, true));
            int code = new CommandLine(new LicenseTool()).execute(args);
            return new Result(code, out.toString(), err.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private Path keyPair() {
        Path key = dir.resolve("keys/private.pem");
        assertThat(run("keygen", "--out", key.toString(), "--public-out", dir.resolve("keys/public.pem").toString()).exitCode()).isZero();
        return key;
    }

    private LicenseVerifier verifier() throws IOException {
        return new LicenseVerifier(LicenseVerifier.readPublicKey(Files.readString(dir.resolve("keys/public.pem"))), Clock.systemUTC());
    }

    @Test
    void keygenWritesAKeyPairAndTheDirectoriesItNeeds() throws IOException {
        Path key = keyPair();

        assertThat(Files.readString(key)).startsWith("-----BEGIN PRIVATE KEY-----");
        assertThat(Files.readString(dir.resolve("keys/public.pem"))).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(Files.getPosixFilePermissions(key)).as("only the owner may read the private key").hasSize(2);
    }

    @Test
    void keygenDoesNotReplaceAKeyByAccident() throws IOException {
        Path key = keyPair();
        String before = Files.readString(key);

        Result again = run("keygen", "--out", key.toString(), "--public-out", dir.resolve("keys/public.pem").toString());

        assertThat(again.exitCode()).isEqualTo(1);
        assertThat(again.err()).contains("exists", "--force");
        assertThat(Files.readString(key)).isEqualTo(before);
    }

    @Test
    void keygenWithForceReplacesTheKey() throws IOException {
        Path key = keyPair();
        String before = Files.readString(key);

        assertThat(run("keygen", "--force", "--out", key.toString(), "--public-out", dir.resolve("keys/public.pem").toString()).exitCode()).isZero();

        assertThat(Files.readString(key)).isNotEqualTo(before);
    }

    @Test
    void issueWritesALicenseThatTheMatchingPublicKeyAccepts() throws IOException {
        Path key = keyPair();
        Path out = dir.resolve("acme.license");

        Result result = run("issue", "--key", key.toString(), "--licensee", "Acme Corp", "--id", "A-1", "--expires", "2099-12-31",
                "--feature", "architecture", "--out", out.toString());

        assertThat(result.exitCode()).as(result.toString()).isZero();
        License license = verifier().verify(Files.readString(out));
        assertThat(license.id()).isEqualTo("A-1");
        assertThat(license.expires()).isEqualTo(LocalDate.of(2099, 12, 31));
        assertThat(license.features()).containsExactly(Feature.ARCHITECTURE);
    }

    @Test
    void withoutFeaturesAllAreUnlockedAndWithoutExpiryTheLicenseNeverRunsOut() throws IOException {
        Path key = keyPair();

        Result result = run("issue", "--key", key.toString(), "--licensee", "Acme Corp");

        License license = verifier().verify(result.out());
        assertThat(license.features()).containsExactlyInAnyOrder(Feature.values());
        assertThat(license.expires()).isNull();
        assertThat(license.id()).hasSize(8);
    }

    @Test
    void anUnknownFeatureIsRefusedAndNamesTheKnownOnes() throws IOException {
        Result result = run("issue", "--key", keyPair().toString(), "--licensee", "Acme", "--feature", "telepathy");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).contains("unknown feature 'telepathy'", "architecture");
        assertThat(result.out()).isEmpty();
    }

    @Test
    void aLicenseThatExpiresBeforeItIsIssuedIsRefused() throws IOException {
        Result result = run("issue", "--key", keyPair().toString(), "--licensee", "Acme", "--issued", "2026-05-01", "--expires", "2026-01-01");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).contains("expire before it is issued");
    }

    @Test
    void anExpiredLicenseCanBeIssuedForATestAndIsRefusedByTheVerifier() throws IOException {
        Result result = run("issue", "--key", keyPair().toString(), "--licensee", "Acme", "--issued", "2020-01-01", "--expires", "2020-12-31");

        assertThat(result.exitCode()).isZero();
        assertThatThrownBy(() -> verifier().verify(result.out())).hasMessageContaining("expired on 2020-12-31");
    }
}
