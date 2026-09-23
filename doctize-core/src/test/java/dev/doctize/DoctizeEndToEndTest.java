package dev.doctize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.config.ProjectInfo;
import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Parameter;
import dev.doctize.model.Placement;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import dev.doctize.model.SourceModelJson;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctizeEndToEndTest {

    @TempDir
    Path dir;

    private Path model;
    private Path guides;
    private Path output;

    @BeforeEach
    void setUp() throws IOException {
        model = dir.resolve("model.json");
        try (var out = Files.newOutputStream(model)) {
            SourceModelJson.write(storeModel(), out);
        }
        guides = dir.resolve("docs");
        write("docs/index.md", "Welcome to the **store**.");
        write("docs/quick-start/first.md", "# First steps\n\nSee [setup](../setup/install.md) and {@link Store#get(String)}.\n\n![pic](../img/a.svg)");
        write("docs/setup/install.md", "# Install\n\n## Gradle\n\nText.");
        write("docs/img/a.svg", "<svg/>");
        output = dir.resolve("site");
    }

    private void write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String read(String path) throws IOException {
        return Files.readString(output.resolve(path));
    }

    private static SourceModel storeModel() {
        Declaration getter = new Declaration(
                ElementKind.METHOD, "get", "com.acme.Store.get", "com.acme", "public String get(String key)",
                List.of(), List.of(new Parameter("key", "String")), "String", List.of());
        Declaration store = new Declaration(
                ElementKind.CLASS, "Store", "com.acme.Store", "com.acme", "public class Store", List.of(), List.of(), null, List.of());
        SourceElement member = new SourceElement(
                Role.API, getter, "Reads a value.\n\n@param key the key to read\n@return the value, never {@code null}",
                Placement.none(), List.of(), List.of());
        SourceElement type = new SourceElement(
                Role.API, store, "A store of values. Read with {@link #get(String)}.\n\n@since 1.0",
                new Placement("Reference", "", 5, ""), List.of("storage"), List.of(member));
        return SourceModel.of(List.of(type));
    }

    private Doctize.Builder builder() {
        return Doctize.builder().model(model).guides(guides).output(output).baseDirectory(dir);
    }

    @Test
    void generatesPagesNavigationLinksAndSearchIndex() throws IOException {
        GenerationReport report = builder()
                .section("Quick start")
                .section("Setup")
                .section("Reference")
                .build()
                .generate();

        assertThat(report.pages()).isEqualTo(3);
        assertThat(report.sections()).isEqualTo(3);

        String home = read("index.html");
        assertThat(home).contains("Welcome to the <strong>store</strong>").contains("Get started");

        String first = read("quick-start/first.html");
        assertThat(first).contains("<h1>First steps</h1>")
                .contains("href=\"../setup/install.html\"")
                .contains("href=\"../reference/com.acme.Store.html#method-get-String\"")
                .contains("src=\"../assets/content/img/a.svg\"");
        assertThat(output.resolve("assets/content/img/a.svg")).exists();

        String type = read("reference/com.acme.Store.html");
        assertThat(type).contains("id=\"method-get-String\"")
                .contains("the key to read")
                .contains("<a href=\"#method-get-String\"><code>get(String)</code></a>");

        String index = read("search-index.js");
        assertThat(index).contains("\"url\":\"reference/com.acme.Store.html#method-get-String\"").contains("storage");

        assertThat(output.resolve("assets/css/theme.css")).exists();
        assertThat(output.resolve("assets/js/search-engine.js")).exists();
    }

    @Test
    void navigationFollowsTheDeclaredSectionOrder() throws IOException {
        builder().section("Reference").section("Setup").section("Quick start").build().generate();

        String home = read("index.html");

        assertThat(home.indexOf(">Reference</summary>")).isLessThan(home.indexOf(">Setup</summary>"));
        assertThat(home.indexOf(">Setup</summary>")).isLessThan(home.indexOf(">Quick start</summary>"));
    }

    @Test
    void customThemeOverridesPartsAndConfigAddsAssets() throws IOException {
        write("theme/templates/partials/footer.mustache", "<footer id=\"mine\">{{project.name}} custom</footer>");
        write("brand.css", ":root{--accent:red}");
        Path config = dir.resolve("doctize.yml");
        Files.writeString(config, """
                project:
                  name: Acme
                  footer: Bye
                theme:
                  path: theme
                  accent: "#123456"
                  extraCss: [brand.css]
                """);

        builder().configFile(config).build().generate();

        String page = read("setup/install.html");
        assertThat(page).contains("<footer id=\"mine\">Acme custom</footer>")
                .contains("--accent: #123456")
                .contains("href=\"../assets/custom/brand.css\"")
                .contains("<a class=\"brand\"");
        assertThat(output.resolve("assets/custom/brand.css")).exists();
    }

    @Test
    void additionalPageSourcesContributePages() throws IOException {
        Page changelog = new Page("changelog", "Changelog", "Project", "", 1, "What changed.", List.of(), new PageContent.Guide("## 1.0\n\nFirst.", ""));

        builder().pageSource(() -> List.of(changelog)).build().generate();

        assertThat(read("project/changelog.html")).contains("<h1>Changelog</h1>").contains("What changed.").contains("id=\"1.0\"");
    }

    @Test
    void regeneratingRemovesStalePagesButOnlyInDirectoriesDoctizeCreated() throws IOException {
        builder().build().generate();
        write("site/stale.html", "old");
        builder().build().generate();
        assertThat(output.resolve("stale.html")).doesNotExist();

        Path foreign = dir.resolve("foreign");
        write("foreign/keep-me.txt", "important");
        Doctize.builder().model(model).output(foreign).build().generate();

        assertThat(foreign.resolve("keep-me.txt")).exists();
    }

    @Test
    void warnsOnceAboutLinkTagsThatPointNowhereButNotAboutJdkTypes() throws IOException {
        write("docs/setup/install.md", "# Install\n\nSee {@link Missing#thing()} and {@link Missing#thing()}, {@link String}, {@link java.time.Duration} and {@link Store}.");

        GenerationReport report = builder().build().generate();

        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().getFirst()).contains("setup/install.html").contains("{@link Missing#thing()}");
    }

    @Test
    void githubPagesOutputHasTheFilesGithubExpects() throws IOException {
        GenerationReport report = builder().githubPages().baseUrl("https://acme.github.io/store").customDomain("docs.acme.dev").build().generate();

        assertThat(report.warnings()).isEmpty();
        assertThat(read(".nojekyll")).isEmpty();
        assertThat(read("CNAME")).isEqualTo("docs.acme.dev\n");
        assertThat(read("robots.txt")).contains("Sitemap: https://acme.github.io/store/sitemap.xml");
        assertThat(read("sitemap.xml"))
                .contains("<loc>https://acme.github.io/store/</loc>")
                .contains("<loc>https://acme.github.io/store/setup/install.html</loc>")
                .contains("<loc>https://acme.github.io/store/reference/com.acme.Store.html</loc>");
        assertThat(read("setup/install.html"))
                .contains("<link rel=\"canonical\" href=\"https://acme.github.io/store/setup/install.html\">");
    }

    @Test
    void notFoundPageUsesAbsolutePathsSoItWorksAtAnyDepth() throws IOException {
        builder().baseUrl("https://acme.github.io/store/").build().generate();

        String notFound = read("404.html");

        assertThat(notFound).contains("Page not found")
                .contains("href=\"/store/assets/css/theme.css\"")
                .contains("src=\"/store/search-index.js\"")
                .contains("href=\"/store/index.html\"")
                .contains("href=\"/store/setup/install.html\"")
                .contains("basePath: \"/store/\"")
                .doesNotContain("canonical");
    }

    @Test
    void githubPagesAddressIsDerivedFromTheRepositoryOfTheProject() throws IOException {
        Path config = dir.resolve("doctize.yml");
        Files.writeString(config, "project:\n  repository: https://github.com/Acme/store\nhosting:\n  githubPages: true\n");

        GenerationReport report = builder().configFile(config).build().generate();

        assertThat(report.warnings()).isEmpty();
        assertThat(read("robots.txt")).contains("https://acme.github.io/store/sitemap.xml");
        assertThat(output.resolve(".nojekyll")).exists();
    }

    @Test
    void withoutAnAddressThereIsNo404OrSitemapAndGithubPagesUsersAreTold() {
        GenerationReport plain = builder().build().generate();
        assertThat(plain.warnings()).isEmpty();
        assertThat(output.resolve("404.html")).doesNotExist();
        assertThat(output.resolve("sitemap.xml")).doesNotExist();
        assertThat(output.resolve(".nojekyll")).doesNotExist();

        GenerationReport pages = builder().githubPages().build().generate();

        assertThat(output.resolve(".nojekyll")).exists();
        assertThat(output.resolve("CNAME")).doesNotExist();
        assertThat(pages.warnings()).singleElement().asString().contains("address of the site is unknown").contains("hosting.baseUrl");
    }

    @Test
    void anAddressAloneAddsTheSeoFilesButNothingSpecificToGithub() {
        builder().baseUrl("https://docs.example.org/").build().generate();

        assertThat(output.resolve("404.html")).exists();
        assertThat(output.resolve("sitemap.xml")).exists();
        assertThat(output.resolve(".nojekyll")).doesNotExist();
    }

    @Test
    void reportsAnEmptyProjectClearly() {
        Path empty = dir.resolve("empty");

        assertThatThrownBy(() -> Doctize.builder().baseDirectory(empty).output(output).build().generate())
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("Nothing to document")
                .hasMessageContaining(empty.resolve("build/classes/java/main").toString())
                .hasMessageContaining("doctize-processor")
                .hasMessageContaining(empty.resolve("docs").toString());
    }

    @Test
    void aProjectWithTheUsualLayoutNeedsNoConfiguration() throws IOException {
        Path classes = dir.resolve("build/classes/java/main/META-INF/doctize");
        Files.createDirectories(classes);
        Files.copy(model, classes.resolve("model.json"));
        write(".git/config", "[remote \"origin\"]\n\turl = git@github.com:acme/store.git\n");

        GenerationReport report = Doctize.builder().baseDirectory(dir).output(output).build().generate();

        assertThat(report.pages()).isGreaterThanOrEqualTo(3);
        assertThat(output.resolve("reference/com.acme.Store.html")).exists();
        assertThat(output.resolve("setup/install.html")).exists();
        assertThat(read("index.html")).contains("Welcome to the <strong>store</strong>").contains("https://github.com/acme/store");
    }

    @Test
    void theConfigurationWinsOverTheProjectDataOfTheBuildTool() throws IOException {
        Path config = dir.resolve("doctize.yml");
        Files.writeString(config, "project:\n  name: Acme Store\n  repository: https://github.com/acme/elsewhere\n");
        write(".git/config", "[remote \"origin\"]\n\turl = git@github.com:acme/store.git\n");

        builder().configFile(config).projectDefaults(new ProjectInfo("store", "Stores values.", "3.1.0", null, null)).build().generate();

        assertThat(read("index.html"))
                .contains("<title>Acme Store</title>")
                .contains("3.1.0")
                .contains("Stores values.")
                .contains("https://github.com/acme/elsewhere").doesNotContain("https://github.com/acme/store\"");
    }
}
