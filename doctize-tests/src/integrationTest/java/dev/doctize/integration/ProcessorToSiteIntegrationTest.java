package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.GenerationReport;
import dev.doctize.model.ElementKind;
import dev.doctize.model.ModelLocator;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole chain in one process: real {@code javac} with the real annotation processor produces the model,
 * the generator turns it and the guides into a site. Nothing is mocked and no model is hand-written.
 */
class ProcessorToSiteIntegrationTest {

    @TempDir
    static Path work;

    static Path classes;
    static Path site;
    static GenerationReport report;

    @BeforeAll
    static void generate() {
        Path project = SampleProject.copy("sample-shop", work);
        classes = SampleProject.compile(project);
        site = work.resolve("site");
        report = SampleProject.generate(project, classes, site, builder -> builder);
    }

    private static String page(String path) throws IOException {
        return Files.readString(site.resolve(path));
    }

    /** The readable text of a page: signatures are syntax-highlighted, so their words are split by markup. */
    private static String text(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    @Test
    void theProcessorExtractsEverythingThatIsAnnotated() {
        SourceModel model = ModelLocator.load(classes);

        assertThat(model.elements()).extracting(e -> e.declaration().qualifiedName()).containsExactlyInAnyOrder(
                "shop.Cart", "shop.Cart.Line", "shop.Item", "shop.Money", "shop.Status", "shop.Pricing", "shop");
        SourceElement pkg = model.elements().stream().filter(e -> e.declaration().kind() == ElementKind.PACKAGE).findFirst().orElseThrow();
        assertThat(pkg.role()).isEqualTo(Role.PAGE);
        assertThat(pkg.comment()).contains("# Pricing rules");
    }

    @Test
    void generatesWithoutWarningsAndTheExpectedStructure() throws IOException {
        assertThat(report.warnings()).isEmpty();
        assertThat(report.sections()).isEqualTo(5);
        assertThat(report.pages()).isEqualTo(10);

        String home = page("index.html");
        assertThat(home).contains("Sample Shop", "A tiny shopping cart used to test Doctize.", "Welcome to the <strong>sample shop</strong>");
        assertThat(home.indexOf(">Quick start</summary>")).isLessThan(home.indexOf(">Setup</summary>"));
        assertThat(home.indexOf(">Setup</summary>")).isLessThan(home.indexOf(">Guides</summary>"));
        assertThat(home.indexOf(">Guides</summary>")).isLessThan(home.indexOf(">Reference</summary>"));
        assertThat(home.indexOf(">Reference</summary>")).isLessThan(home.indexOf(">Recipes</summary>"));
    }

    @Test
    void anApiPageShowsSignaturesOverloadsParametersExceptionsAndTags() throws IOException {
        String cart = page("reference/shop.Cart.html");

        assertThat(cart).contains("<h1>Cart</h1>")
                .contains("add(Item)", "add(Item, int)")
                .contains("id=\"method-add-Item\"", "id=\"method-add-Item-int\"")
                .contains("the item to add", "how many pieces, at least one")
                .contains("<h4>Throws</h4>", "IllegalArgumentException")
                .contains("A cart never contains the same item twice")
                .contains("markdown-alert-tip")
                .contains("id=\"methods\"")
                .doesNotContain("rebuildIndex", "Not part of the public documentation");
        assertThat(text(cart)).contains("public final class Cart")
                .contains("public Cart add(Item item, int quantity)")
                .contains("public static Cart empty()");
    }

    @Test
    void deprecatedMembersAreMarkedAndKeepTheirReason() throws IOException {
        String cart = page("reference/shop.Cart.html");

        assertThat(cart).contains("Deprecated.", "this method will be removed in 2.0");
        assertThat(text(cart)).contains("@Deprecated(since = \"1.1\", forRemoval = true)")
                .contains("public Cart addAll(Item... items)");
    }

    @Test
    void recordsEnumsAndInterfacesAreDescribedInTheirOwnTerms() throws IOException {
        String item = page("reference/shop.Item.html");
        assertThat(text(item)).contains("public record Item(String sku, Money price)");
        assertThat(item).contains("the stock keeping unit");
        assertThat(page("reference/shop.Status.html")).contains("id=\"constants\"", "Paid and on its way.").doesNotContain("valueOf");
        String pricing = page("reference/shop.Pricing.html");
        assertThat(text(pricing)).contains("Money price(Cart cart)", "default boolean beforeTaxes()").doesNotContain("public abstract");
    }

    @Test
    void aNestedTypeWithItsOwnAnnotationGetsItsOwnPageAndIsLinkedFromItsOwner() throws IOException {
        String line = page("reference/shop.Cart.Line.html");

        assertThat(line).contains("<h1>Cart.Line</h1>", "subtotal()", "The price of the whole line.");
        assertThat(page("reference/shop.Cart.html")).contains("Nested types");
    }

    @Test
    void thePackageCommentBecomesAProsePageWithResolvedLinks() throws IOException {
        String pricing = page("guides/pricing-rules.html");

        assertThat(pricing).contains("<h1>Pricing rules</h1>", "<table>", "<ol>")
                .contains("href=\"../reference/shop.Cart.html#method-total\"")
                .contains("href=\"../reference/shop.Cart.Line.html#method-subtotal\"")
                .contains("href=\"../reference/shop.Cart.Line.html\"");
    }

    @Test
    void guidesLinkToEachOtherToApiPagesToAssetsAndReferenceLinksResolve() throws IOException {
        String start = page("quick-start/start.html");

        assertThat(start).contains("<h1>Start shopping</h1>", "Put something in a cart.")
                .contains("href=\"../setup/install.html\"")
                .contains("href=\"../guides/pricing-rules.html\"")
                .contains("href=\"../reference/shop.Cart.html#method-add-Item\"")
                .contains("href=\"../reference/shop.Cart.html#method-add-Item-int\"")
                .contains("href=\"../reference/shop.Money.html\"")
                .contains("src=\"../assets/content/images/cart.svg\"")
                .contains("class=\"tabs\"", "data-tab=\"Gradle\"", "data-tab=\"Maven\"", "<div class=\"code-title\">Main.java</div>");
        assertThat(site.resolve("assets/content/images/cart.svg")).exists();
        assertThat(page("setup/install.html")).contains("href=\"../quick-start/start.html#your-first-cart\"");
    }

    @Test
    void aFolderThatIsNotDeclaredStillBecomesASection() throws IOException {
        assertThat(page("recipes/discounts.html")).contains("<h1>Discounts</h1>");
        assertThat(page("index.html")).contains(">Recipes</summary>");
    }

    @Test
    void theSearchIndexReachesMembersAndKeywords() throws IOException {
        String index = page("search-index.js");

        assertThat(index).contains("\"url\":\"reference/shop.Cart.html#method-add-Item-int\"")
                .contains("basket checkout")
                .contains("\"heading\":\"add(Item, int)\"");
    }
}
