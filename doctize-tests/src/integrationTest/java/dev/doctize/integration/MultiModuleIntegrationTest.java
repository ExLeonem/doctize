package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Doctize;
import dev.doctize.GenerationReport;
import dev.doctize.testing.SiteCrawler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Several modules are documented as one site, and links cross module boundaries. */
class MultiModuleIntegrationTest {

    @TempDir
    Path work;

    @Test
    void typesOfAllModulesEndUpInOneSiteAndLinkToEachOther() throws IOException {
        Path shop = SampleProject.copy("sample-shop", work);
        Path payments = SampleProject.copy("sample-payments", work);
        Path shopClasses = SampleProject.compile(shop);
        Path paymentClasses = SampleProject.compile(payments);
        Path site = work.resolve("site");

        GenerationReport report = Doctize.builder()
                .configFile(shop.resolve("doctize.yml"))
                .model(shopClasses)
                .model(paymentClasses)
                .output(site)
                .build()
                .generate();

        assertThat(report.warnings()).isEmpty();
        assertThat(report.pages()).isEqualTo(11);
        String gateway = Files.readString(site.resolve("reference/payments.Gateway.html"));
        assertThat(gateway).contains("<h1>Gateway</h1>")
                .contains("href=\"../reference/shop.Cart.html\"")
                .contains("href=\"../reference/shop.Cart.html#method-total\"");
        assertThat(Files.readString(site.resolve("reference/shop.Cart.html"))).contains("Cart");
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    @Test
    void linkingToAModuleThatIsNotDocumentedIsReportedAsAWarning() {
        Path payments = SampleProject.copy("sample-payments", work);
        Path paymentClasses = SampleProject.compile(payments);

        GenerationReport report = Doctize.builder().model(paymentClasses).output(work.resolve("site")).build().generate();

        assertThat(report.warnings()).hasSize(2)
                .allSatisfy(warning -> assertThat(warning).contains("payments.Gateway.html").contains("shop.Cart"));
    }
}
