package dev.doctize.system;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.testing.SiteCrawler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Gradle plugin as a user gets it: a real Gradle build applies {@code id("dev.doctize")} from the staged artifacts,
 * and everything else it needs (the CLI, the annotations, the processor) is resolved from there too.
 */
class GradlePluginSystemTest {

    static final String VERSION = System.getProperty("doctize.version");
    static final Path STAGING = Path.of(System.getProperty("doctize.staging.repository"));

    @TempDir
    Path work;

    Path project;

    @BeforeEach
    void aProjectThatAppliesThePlugin() throws IOException {
        project = SamplesForSystemTests.copy("sample-shop", work);
        Files.writeString(project.resolve("settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        maven { url = uri("%s") }
                    }
                }
                rootProject.name = "shop"
                """.formatted(STAGING.toUri()));
        Files.writeString(project.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("dev.doctize") version "%s"
                }

                repositories {
                    maven {
                        url = uri("%s")
                        content { includeGroup("dev.doctize") }
                    }
                    mavenCentral()
                }

                java {
                    toolchain { languageVersion = JavaLanguageVersion.of(25) }
                }

                sourceSets.main { java.srcDir("java") }
                """.formatted(VERSION, STAGING.toUri()));
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private GradleRunner runner(String... arguments) {
        String[] all = new String[arguments.length + 1];
        all[0] = "--stacktrace";
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        return GradleRunner.create().withProjectDir(project.toFile()).withArguments(all).forwardOutput();
    }

    @Test
    void aJavaProjectNeedsNothingButThePluginToGenerateItsDocumentation() throws IOException {
        BuildResult result = run("generateDocs");

        assertThat(result.task(":compileJava").getOutcome()).as("the classes, and with them the model, are built first").isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains("Generated 10 pages in 5 sections");
        Path site = project.resolve("build/docs");
        assertThat(Files.readString(site.resolve("reference/shop.Cart.html"))).contains("add(Item, int)", "the item to add");
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    @Test
    void theSiteIsOnlyGeneratedAgainWhenSomethingChanged() throws IOException {
        run("generateDocs");

        assertThat(run("generateDocs").task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        Files.writeString(project.resolve("docs/setup/install.md"), Files.readString(project.resolve("docs/setup/install.md")) + "\nAn addition.\n");
        assertThat(run("generateDocs").task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        Path cart = project.resolve("java/shop/Cart.java");
        Files.writeString(cart, Files.readString(cart).replace("One line of the cart.", "One line of the cart, as edited."));
        assertThat(run("generateDocs").task(":generateDocs").getOutcome()).as("a changed doc comment changes the model").isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(project.resolve("build/docs/reference/shop.Cart.html"))).contains("as edited.");
    }

    @Test
    void optionsOfTheTaskReachTheTool() {
        run("generateDocs", "--github-pages", "--base-url=https://acme.github.io/shop/");

        Path site = project.resolve("build/docs");
        assertThat(site.resolve(".nojekyll")).exists();
        assertThat(site.resolve("sitemap.xml")).exists();
        assertThat(site.resolve("404.html")).exists();
    }

    @Test
    void theDoctizeArgsPropertyOfTheGithubPagesWorkflowIsUnderstood() {
        run("generateDocs", "-PdoctizeArgs=--github-pages --base-url https://acme.github.io/shop/");

        assertThat(project.resolve("build/docs/.nojekyll")).exists();
        assertThat(project.resolve("build/docs/sitemap.xml")).exists();
    }

    @Test
    void theDoctizeBlockConfiguresTheTask() throws IOException {
        Files.writeString(project.resolve("build.gradle.kts"), Files.readString(project.resolve("build.gradle.kts")) + """

                doctize {
                    output = layout.buildDirectory.dir("site")
                    githubPages = true
                    baseUrl = "https://acme.github.io/shop/"
                }
                """);

        run("generateDocs");

        assertThat(project.resolve("build/site/index.html")).exists();
        assertThat(project.resolve("build/site/.nojekyll")).exists();
        assertThat(project.resolve("build/docs")).doesNotExist();
    }

    @Test
    void aBrokenLinkFailsTheBuildWhenWarningsAreErrors() throws IOException {
        Files.writeString(project.resolve("docs/guides-broken.md"), "# Broken\n\nSee {@link NoSuchType}.\n");

        BuildResult tolerated = run("generateDocs");
        assertThat(tolerated.getOutput()).contains("NoSuchType");

        BuildResult failed = runner("generateDocs", "--rerun-tasks", "--fail-on-warnings").buildAndFail();
        assertThat(failed.getOutput()).contains("does not refer to a documented type or member", "doctize generate failed with exit value 1");
    }

    @Test
    void theTaskWorksWithTheConfigurationCache() {
        run("generateDocs", "--configuration-cache");
        BuildResult second = run("generateDocs", "--configuration-cache", "--rerun-tasks");

        assertThat(second.getOutput()).contains("Reusing configuration cache");
        assertThat(second.task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void theArchitectureMapWithoutAUsableLicenseIsSkippedByThePlugin() throws IOException {
        Files.writeString(project.resolve("garbage.license"), "not a license\n");
        Files.writeString(project.resolve("build.gradle.kts"), Files.readString(project.resolve("build.gradle.kts")) + """

                doctize {
                    license = layout.projectDirectory.file("garbage.license")
                    architecture = true
                }
                """);

        BuildResult result = run("generateDocs");

        assertThat(result.task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains("The architecture map was skipped.", "not a Doctize license");
        assertThat(project.resolve("build/docs/index.html")).exists();
        assertThat(project.resolve("build/docs/architecture")).doesNotExist();
    }

    @Test
    void withALicenseOfTheVendorThePluginGeneratesTheMapAndRunsAgainWhenTheLicenseChanges() throws IOException {
        java.security.PrivateKey key = dev.doctize.testing.TestLicenses.vendorKey().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(key != null, "the private key of the vendor is not available here");
        // doctize.license next to the build is found by default.
        dev.doctize.testing.TestLicenses.writeVendorLicense(project.resolve("doctize.license"), key);

        BuildResult first = run("generateDocs", "--architecture");

        assertThat(first.task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.getOutput()).contains("Licensed to Test Licensee");
        assertThat(Files.readString(project.resolve("build/docs/architecture/architecture.html"))).contains("<svg class=\"arch-map\"");
        assertThat(run("generateDocs", "--architecture").task(":generateDocs").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        Files.writeString(project.resolve("doctize.license"), "not a license\n");
        BuildResult second = run("generateDocs", "--architecture");
        assertThat(second.task(":generateDocs").getOutcome()).as("a changed license is an input").isEqualTo(TaskOutcome.SUCCESS);
        assertThat(second.getOutput()).contains("The architecture map was skipped.");
    }

    @Test
    void severalModulesAreDocumentedAsOneSiteAndLinkToEachOther() throws IOException {
        Path multi = work.resolve("multi");
        Path shop = SamplesForSystemTests.copy("sample-shop", work.resolve("samples"));
        Path payments = SamplesForSystemTests.copy("sample-payments", work.resolve("samples"));
        Files.createDirectories(multi.resolve("shop/src/main"));
        Files.createDirectories(multi.resolve("payments/src/main"));
        Files.move(shop.resolve("java"), multi.resolve("shop/src/main/java"));
        Files.move(payments.resolve("java"), multi.resolve("payments/src/main/java"));
        // The site project has no code: it collects the modules, and holds the guides and the configuration.
        Files.createDirectories(multi.resolve("site"));
        Files.move(shop.resolve("docs"), multi.resolve("site/docs"));
        Files.move(shop.resolve("doctize.yml"), multi.resolve("site/doctize.yml"));

        Files.writeString(multi.resolve("settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        maven { url = uri("%s") }
                    }
                }
                rootProject.name = "multi"
                include("shop", "payments", "site")
                """.formatted(STAGING.toUri()));
        Files.writeString(multi.resolve("build.gradle.kts"), """
                plugins {
                    id("dev.doctize") version "%s" apply false
                }

                subprojects {
                    repositories {
                        maven {
                            url = uri("%s")
                            content { includeGroup("dev.doctize") }
                        }
                        mavenCentral()
                    }
                }
                """.formatted(VERSION, STAGING.toUri()));
        for (String module : new String[] {"shop", "payments"}) {
            Files.writeString(multi.resolve(module + "/build.gradle.kts"), """
                    plugins {
                        `java-library`
                        id("dev.doctize")
                    }

                    java {
                        toolchain { languageVersion = JavaLanguageVersion.of(25) }
                    }
                    """);
        }
        Files.writeString(multi.resolve("site/build.gradle.kts"), """
                plugins {
                    id("dev.doctize")
                }

                doctize {
                    modules(project(":shop"), project(":payments"))
                }
                """);

        BuildResult result = GradleRunner.create().withProjectDir(multi.toFile()).withArguments("--stacktrace", ":site:generateDocs").forwardOutput().build();

        assertThat(result.task(":shop:compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":payments:compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains("Generated 11 pages");
        Path site = multi.resolve("site/build/docs");
        assertThat(Files.readString(site.resolve("reference/payments.Gateway.html"))).contains("href=\"../reference/shop.Cart.html\"");
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }
}
