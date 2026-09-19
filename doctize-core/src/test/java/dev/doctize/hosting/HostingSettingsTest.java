package dev.doctize.hosting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import org.junit.jupiter.api.Test;

class HostingSettingsTest {

    private static String resolved(HostingSettings settings, String repository) {
        return settings.resolveBaseUrl(repository).map(BaseUrl::value).orElse("(unknown)");
    }

    @Test
    void derivesTheGithubPagesAddressFromTheRepository() {
        HostingSettings pages = new HostingSettings("", true, "");

        assertThat(resolved(pages, "https://github.com/Acme/cache")).isEqualTo("https://acme.github.io/cache/");
        assertThat(resolved(pages, "https://github.com/acme/cache.git")).isEqualTo("https://acme.github.io/cache/");
        assertThat(resolved(pages, "https://github.com/acme/cache/")).isEqualTo("https://acme.github.io/cache/");
        assertThat(resolved(pages, "https://www.github.com/acme/Cache")).isEqualTo("https://acme.github.io/Cache/");
    }

    @Test
    void aUserSiteLivesAtTheRootOfItsHost() {
        assertThat(resolved(new HostingSettings("", true, ""), "https://github.com/acme/acme.github.io"))
                .isEqualTo("https://acme.github.io/");
    }

    @Test
    void theAddressIsUnknownWithoutGithubPagesOrWithAnUnrelatedRepository() {
        assertThat(resolved(HostingSettings.none(), "https://github.com/acme/cache")).isEqualTo("(unknown)");
        assertThat(resolved(new HostingSettings("", true, ""), "https://gitlab.com/acme/cache")).isEqualTo("(unknown)");
        assertThat(resolved(new HostingSettings("", true, ""), "")).isEqualTo("(unknown)");
    }

    @Test
    void anExplicitAddressBeatsACustomDomainWhichBeatsTheRepository() {
        String repository = "https://github.com/acme/cache";

        assertThat(resolved(new HostingSettings("https://docs.acme.dev/x", true, "other.dev"), repository)).isEqualTo("https://docs.acme.dev/x/");
        assertThat(resolved(new HostingSettings("", true, "docs.acme.dev"), repository)).isEqualTo("https://docs.acme.dev/");
    }

    @Test
    void settingsGivenLaterOverrideEarlierOnes() {
        HostingSettings base = new HostingSettings("https://a.org/", false, "a.org");

        HostingSettings overridden = base.overriddenBy("https://b.org/", true, null);

        assertThat(overridden).isEqualTo(new HostingSettings("https://b.org/", true, "a.org"));
        assertThat(base.overriddenBy(null, null, "").baseUrl()).isEqualTo("https://a.org/");
    }

    @Test
    void rejectsCustomDomainsThatAreNotHostNames() {
        assertThatThrownBy(() -> new HostingSettings("", true, "https://docs.acme.dev"))
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("custom domain");
    }
}
