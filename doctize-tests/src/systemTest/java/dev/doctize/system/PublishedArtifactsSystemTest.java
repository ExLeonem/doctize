package dev.doctize.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Release readiness: what would be uploaded to Maven Central is inspected as a stranger would inspect it,
 * against the requirements of the Central Portal. Runs on the artifacts in {@code build/staging-deploy}.
 */
class PublishedArtifactsSystemTest {

    static final String VERSION = System.getProperty("doctize.version");
    static final Path STAGING = Path.of(System.getProperty("doctize.staging.repository"));

    private static Path directory(String module) {
        return STAGING.resolve("dev/doctize/doctize-" + module + "/" + VERSION);
    }

    private static Path file(String module, String suffix) {
        return directory(module).resolve("doctize-" + module + "-" + VERSION + suffix);
    }

    private static Document pom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream in = Files.newInputStream(file(module, ".pom"))) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    private static String xpath(Document pom, String expression) throws Exception {
        return (String) XPathFactory.newInstance().newXPath().evaluate(expression, pom, XPathConstants.STRING);
    }

    private static Set<String> entries(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream().map(java.util.zip.ZipEntry::getName).collect(Collectors.toSet());
        }
    }

    private static String manifest(Path jar, String attribute) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getManifest().getMainAttributes().getValue(attribute);
        }
    }

    /** Maven checksum file extension to the name of the {@link MessageDigest} algorithm. */
    private static final java.util.Map<String, String> CHECKSUMS =
            java.util.Map.of("md5", "MD5", "sha1", "SHA-1", "sha256", "SHA-256", "sha512", "SHA-512");

    @ParameterizedTest
    @ValueSource(strings = {"annotations", "processor", "core", "cli", "gradle-plugin"})
    void everyModuleShipsAllTheFilesCentralRequiresWithCorrectChecksums(String module) throws Exception {
        for (String suffix : List.of(".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module")) {
            Path artifact = file(module, suffix);
            assertThat(artifact).as(artifact.getFileName().toString()).exists().isNotEmptyFile();
            byte[] content = Files.readAllBytes(artifact);
            for (var checksum : CHECKSUMS.entrySet()) {
                Path checksumFile = Path.of(artifact + "." + checksum.getKey());
                String expected = HexFormat.of().formatHex(MessageDigest.getInstance(checksum.getValue()).digest(content));
                assertThat(checksumFile).as(checksumFile.getFileName().toString()).exists();
                assertThat(Files.readString(checksumFile).trim()).as(checksumFile.getFileName().toString()).isEqualTo(expected);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"annotations", "processor", "core", "cli", "gradle-plugin"})
    void thePomCarriesTheMetadataCentralRequires(String module) throws Exception {
        Document pom = pom(module);

        assertThat(xpath(pom, "/project/groupId")).isEqualTo("dev.doctize");
        assertThat(xpath(pom, "/project/artifactId")).isEqualTo("doctize-" + module);
        assertThat(xpath(pom, "/project/version")).isEqualTo(VERSION);
        assertThat(xpath(pom, "/project/name")).startsWith("Doctize").doesNotContain("null");
        assertThat(xpath(pom, "/project/description")).isNotBlank().doesNotContain("null");
        assertThat(xpath(pom, "/project/url")).isEqualTo("https://github.com/ExLeonem/doctize");
        assertThat(xpath(pom, "/project/licenses/license/name")).isEqualTo("Apache License, Version 2.0");
        assertThat(xpath(pom, "/project/licenses/license/url")).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0.txt");
        assertThat(xpath(pom, "/project/developers/developer/name")).isNotBlank();
        assertThat(xpath(pom, "/project/scm/url")).isEqualTo("https://github.com/ExLeonem/doctize");
        assertThat(xpath(pom, "/project/scm/connection")).startsWith("scm:git:");
        assertThat(xpath(pom, "/project/scm/developerConnection")).startsWith("scm:git:");
    }

    @Test
    void dependenciesAreDeclaredWithTheScopesConsumersNeed() throws Exception {
        assertThat(dependencies("annotations")).as("annotations have no dependencies").isEmpty();
        assertThat(dependencies("core")).contains("com.fasterxml.jackson.core:jackson-databind:compile")
                .contains("org.commonmark:commonmark:runtime", "com.samskivert:jmustache:runtime")
                .noneMatch(dependency -> dependency.startsWith("dev.doctize"))
                .as("the annotations are compileOnly and must not leak into the POM");
        assertThat(dependencies("processor")).contains("dev.doctize:doctize-annotations:runtime", "dev.doctize:doctize-core:runtime");
        assertThat(dependencies("cli")).contains("dev.doctize:doctize-core:runtime", "info.picocli:picocli:runtime");
    }

    private static List<String> dependencies(String module) throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate("/project/dependencies/dependency", pom(module), XPathConstants.NODESET);
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Element dependency = (org.w3c.dom.Element) nodes.item(i);
            String text = dependency.getElementsByTagName("groupId").item(0).getTextContent() + ":"
                    + dependency.getElementsByTagName("artifactId").item(0).getTextContent() + ":"
                    + dependency.getElementsByTagName("scope").item(0).getTextContent();
            String version = dependency.getElementsByTagName("version").item(0).getTextContent();
            assertThat(version).as(text).isNotBlank();
            result.add(text);
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"annotations", "processor", "core", "cli", "gradle-plugin"})
    void jarsAreIdentifiedReproducibleAndCarryTheLicense(String module) throws Exception {
        Path jar = file(module, ".jar");

        // "dev.doctize.gradle-plugin" would not be a valid module name.
        assertThat(manifest(jar, "Automatic-Module-Name")).isEqualTo(module.equals("gradle-plugin") ? "dev.doctize.gradle" : "dev.doctize." + module);
        assertThat(manifest(jar, "Implementation-Version")).isEqualTo(VERSION);
        assertThat(entries(jar)).contains("META-INF/LICENSE");
        try (JarFile file = new JarFile(jar.toFile())) {
            assertThat(file.stream()).as("timestamps are normalised so that the build is reproducible")
                    .allSatisfy(entry -> assertThat(entry.getTimeLocal().getYear()).isEqualTo(1980));
        }
    }

    @Test
    void theCoreJarContainsTheCodeAndTheWholeDefaultTheme() throws IOException {
        Set<String> entries = entries(file("core", ".jar"));

        assertThat(entries).contains("dev/doctize/Doctize.class", "dev/doctize/Doctize$Builder.class", "dev/doctize/hosting/HostingSettings.class")
                .contains("dev/doctize/theme/default/templates/layout.mustache", "dev/doctize/theme/default/templates/not-found.mustache",
                        "dev/doctize/theme/default/assets/css/theme.css", "dev/doctize/theme/default/assets/js/search-engine.js",
                        "dev/doctize/theme/default/assets/js/search-ui.js", "dev/doctize/theme/default/assets/js/app.js",
                        "dev/doctize/theme/default/assets/img/favicon.svg")
                .noneMatch(entry -> entry.contains("Test") || entry.startsWith("sample") || entry.contains("testing"));
    }

    @Test
    void theProcessorJarIsRegisteredWithJavacAndGradle() throws IOException {
        Path jar = file("processor", ".jar");

        assertThat(entries(jar)).contains("dev/doctize/processor/DocProcessor.class",
                "META-INF/services/javax.annotation.processing.Processor", "META-INF/gradle/incremental.annotation.processors");
        try (JarFile file = new JarFile(jar.toFile())) {
            assertThat(new String(file.getInputStream(file.getEntry("META-INF/services/javax.annotation.processing.Processor")).readAllBytes()).trim())
                    .isEqualTo("dev.doctize.processor.DocProcessor");
        }
    }

    @Test
    void theAnnotationsJarIsTinyAndHoldsOnlyTheAnnotations() throws IOException {
        Path jar = file("annotations", ".jar");

        assertThat(entries(jar).stream().filter(e -> e.endsWith(".class")))
                .as("nothing but annotations, and the names they use")
                .containsExactlyInAnyOrder(
                        "dev/doctize/annotations/Doc.class", "dev/doctize/annotations/DocPage.class", "dev/doctize/annotations/DocIgnore.class",
                        "dev/doctize/annotations/DiagramTypes.class", "dev/doctize/annotations/DiagramIgnore.class",
                        "dev/doctize/annotations/DiagramInclude.class", "dev/doctize/annotations/StateMachine.class",
                        "dev/doctize/annotations/Transition.class", "dev/doctize/annotations/Transitions.class", "dev/doctize/annotations/Flow.class");
        assertThat(Files.size(jar)).isLessThan(30_000);
    }

    @Test
    void theGradlePluginIsFoundByItsIdAndCompiledForTheJavaThatRunsGradle() throws Exception {
        Path jar = file("gradle-plugin", ".jar");

        assertThat(entries(jar)).contains("dev/doctize/gradle/DoctizePlugin.class", "META-INF/gradle-plugins/dev.doctize.properties");
        try (JarFile file = new JarFile(jar.toFile())) {
            assertThat(new String(file.getInputStream(file.getEntry("META-INF/gradle-plugins/dev.doctize.properties")).readAllBytes()))
                    .contains("implementation-class=dev.doctize.gradle.DoctizePlugin");
            assertThat(new String(file.getInputStream(file.getEntry("dev/doctize/gradle/version.properties")).readAllBytes()).trim())
                    .isEqualTo("version=" + VERSION);
            byte[] header = file.getInputStream(file.getEntry("dev/doctize/gradle/DoctizePlugin.class")).readNBytes(8);
            assertThat(((header[6] & 0xff) << 8) | (header[7] & 0xff)).as("class file major version: Gradle needs to run on 17, not 25").isEqualTo(61);
        }
        assertThat(dependencies("gradle-plugin")).as("the plugin needs no library: it drives the tool in a separate process").isEmpty();
    }

    @Test
    void theMarkerArtifactLetsGradleResolveThePluginById() throws Exception {
        Path directory = STAGING.resolve("dev/doctize/dev.doctize.gradle.plugin/" + VERSION);
        Path markerPom = directory.resolve("dev.doctize.gradle.plugin-" + VERSION + ".pom");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom;
        try (InputStream in = Files.newInputStream(markerPom)) {
            pom = factory.newDocumentBuilder().parse(in);
        }

        assertThat(xpath(pom, "/project/dependencies/dependency/groupId")).isEqualTo("dev.doctize");
        assertThat(xpath(pom, "/project/dependencies/dependency/artifactId")).isEqualTo("doctize-gradle-plugin");
        assertThat(xpath(pom, "/project/dependencies/dependency/version")).isEqualTo(VERSION);
        for (String required : List.of("/project/name", "/project/description", "/project/url", "/project/licenses/license/name",
                "/project/developers/developer/name", "/project/scm/url")) {
            assertThat(xpath(pom, required)).as(required).isNotBlank().doesNotContain("null");
        }
        assertThat(Path.of(markerPom + ".sha512")).exists();
        if (Boolean.getBoolean("doctize.signed")) {
            assertThat(Path.of(markerPom + ".asc")).as("Central rejects unsigned files, the marker included").exists().isNotEmptyFile();
        }
    }

    @Test
    void theCoreJarCarriesThePublicKeyThatChecksLicensesAndNothingThatCanIssueThem() throws Exception {
        Path core = file("core", ".jar");

        try (JarFile jar = new JarFile(core.toFile())) {
            String key = new String(jar.getInputStream(jar.getEntry("dev/doctize/license/vendor-public-key.pem")).readAllBytes());
            assertThat(key).startsWith("-----BEGIN PUBLIC KEY-----").doesNotContain("PRIVATE");
        }
        assertThat(entries(core)).contains("dev/doctize/license/LicenseVerifier.class", "dev/doctize/license/License.class",
                "dev/doctize/architecture/ArchitecturePageSource.class")
                .noneMatch(entry -> entry.contains("LicenseIssuer") || entry.contains("LicenseTool") || entry.endsWith("private-key.pem"));
        assertThat(STAGING.resolve("dev/doctize/doctize-licensing")).as("the tool that issues licenses is not published").doesNotExist();
        for (String module : List.of("annotations", "processor", "core", "cli", "gradle-plugin")) {
            assertThat(entries(file(module, ".jar"))).as(module).noneMatch(entry -> entry.contains("LicenseIssuer") || entry.contains("licensing/"));
        }
        assertThat(dependencies("cli")).as("the command does not need the tool that issues licenses").noneMatch(dependency -> dependency.contains("licensing"));
    }

    @Test
    void theCoreJarShipsTheMermaidLibraryTogetherWithItsLicense() throws IOException {
        Set<String> entries = entries(file("core", ".jar"));

        assertThat(entries).contains("dev/doctize/diagram/mermaid.min.js", "dev/doctize/diagram/mermaid.LICENSE", "dev/doctize/diagram/mermaid.NOTICE.md",
                "dev/doctize/theme/default/assets/js/diagrams.js");
        try (JarFile file = new JarFile(file("core", ".jar").toFile())) {
            assertThat(new String(file.getInputStream(file.getEntry("dev/doctize/diagram/mermaid.LICENSE")).readAllBytes())).contains("MIT License", "Knut Sveidqvist");
            assertThat(file.getEntry("dev/doctize/diagram/mermaid.min.js").getSize()).isGreaterThan(1_000_000);
        }
    }

    @Test
    void theCliJarKnowsItsOwnVersion() throws IOException {
        try (JarFile file = new JarFile(file("cli", ".jar").toFile())) {
            String properties = new String(file.getInputStream(file.getEntry("dev/doctize/cli/version.properties")).readAllBytes());

            assertThat(properties.trim()).isEqualTo("version=" + VERSION);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"annotations", "processor", "core", "cli", "gradle-plugin"})
    void sourcesAndJavadocAreReallyThere(String module) throws IOException {
        assertThat(entries(file(module, "-sources.jar"))).anyMatch(entry -> entry.endsWith(".java"));
        Set<String> javadoc = entries(file(module, "-javadoc.jar"));
        assertThat(javadoc).contains("index.html").anyMatch(entry -> entry.endsWith("package-summary.html") || entry.endsWith(".html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"annotations", "processor", "core", "cli", "gradle-plugin"})
    void everyFileIsSignedWhenASigningKeyIsConfigured(String module) {
        assumeTrue(Boolean.getBoolean("doctize.signed"), "no signing key configured; this is checked in the release build");

        for (String suffix : List.of(".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module")) {
            assertThat(Path.of(file(module, suffix) + ".asc")).as(suffix).exists().isNotEmptyFile();
        }
    }
}
