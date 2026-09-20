package dev.doctize.licensing;

import dev.doctize.license.Feature;
import dev.doctize.license.License;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** The vendor's command line tool: {@code keygen} creates the signing key, {@code issue} signs a license. */
@Command(
        name = "doctize-license",
        description = "Creates the signing key and issues licenses for Doctize.",
        mixinStandardHelpOptions = true,
        subcommands = {LicenseTool.KeyGen.class, LicenseTool.Issue.class})
public final class LicenseTool implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new LicenseTool()).execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "keygen", description = "Creates a signing key pair. Keep the private key secret; the public key goes into doctize-core.", mixinStandardHelpOptions = true)
    static final class KeyGen implements Callable<Integer> {

        @Option(names = "--out", required = true, description = "Where to write the private key. Never commit it.")
        Path privateKey;

        @Option(names = "--public-out", required = true, description = "Where to write the public key, e.g. doctize-core/src/main/resources/dev/doctize/license/vendor-public-key.pem.")
        Path publicKey;

        @Option(names = "--force", description = "Overwrite existing files. Licenses signed with a replaced key stop working.")
        boolean force;

        @Override
        public Integer call() throws IOException {
            for (Path target : List.of(privateKey, publicKey)) {
                if (Files.exists(target) && !force) {
                    System.err.println("doctize-license: " + target + " exists; pass --force to replace it (licenses signed with the old key stop working).");
                    return 1;
                }
            }
            for (Path target : List.of(privateKey, publicKey)) {
                Files.createDirectories(target.toAbsolutePath().getParent());
            }
            KeyPair pair = LicenseIssuer.generateKeys();
            Files.writeString(publicKey, LicenseIssuer.toPem(pair.getPublic()));
            Files.writeString(privateKey, LicenseIssuer.toPem(pair.getPrivate()));
            try {
                Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Not a POSIX file system: nothing more can be done here.
            }
            System.out.println("Private key: " + privateKey + " (keep it secret)");
            System.out.println("Public key:  " + publicKey);
            return 0;
        }
    }

    @Command(name = "issue", description = "Issues a license.", mixinStandardHelpOptions = true)
    static final class Issue implements Callable<Integer> {

        @Option(names = "--key", required = true, description = "The private key created by keygen.")
        Path privateKey;

        @Option(names = "--licensee", required = true, description = "Who the license is for.")
        String licensee;

        @Option(names = "--id", description = "Identifier of the license. Default: a random one.")
        String id;

        @Option(names = "--issued", description = "Issue date, yyyy-mm-dd. Default: today.")
        LocalDate issued;

        @Option(names = "--expires", description = "Last day of validity, yyyy-mm-dd. Default: the license does not expire.")
        LocalDate expires;

        @Option(names = "--feature", description = "A feature to unlock, repeatable. Default: all of them. One of: ${COMPLETION-CANDIDATES}.")
        List<String> features = List.of();

        @Option(names = "--out", description = "Write the license to this file instead of standard output.")
        Path out;

        @Override
        public Integer call() throws IOException {
            LocalDate day = issued == null ? LocalDate.now() : issued;
            if (expires != null && expires.isBefore(day)) {
                System.err.println("doctize-license: the license would expire before it is issued.");
                return 1;
            }
            Set<Feature> granted = EnumSet.noneOf(Feature.class);
            for (String name : features) {
                Feature feature = Feature.byId(name).orElse(null);
                if (feature == null) {
                    System.err.println("doctize-license: unknown feature '" + name + "'. Known: " + Arrays.stream(Feature.values()).map(Feature::id).toList());
                    return 1;
                }
                granted.add(feature);
            }
            if (features.isEmpty()) {
                granted.addAll(EnumSet.allOf(Feature.class));
            }
            String licenseId = id == null ? UUID.randomUUID().toString().substring(0, 8) : id;
            String text = LicenseIssuer.issue(
                    new License(licenseId, licensee, day, expires, granted),
                    LicenseIssuer.readPrivateKey(Files.readString(privateKey)));
            if (out == null) {
                System.out.print(text);
            } else {
                Files.writeString(out, text);
                System.out.println("License " + licenseId + " written to " + out);
            }
            return 0;
        }
    }
}
