package dev.doctize.hosting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import org.junit.jupiter.api.Test;

class BaseUrlTest {

    @Test
    void endsWithASlashAndKnowsItsPath() {
        assertThat(BaseUrl.of("https://acme.github.io/cache").value()).isEqualTo("https://acme.github.io/cache/");
        assertThat(BaseUrl.of("https://acme.github.io/cache/").pathPrefix()).isEqualTo("/cache/");
        assertThat(BaseUrl.of("https://docs.acme.dev").pathPrefix()).isEqualTo("/");
        assertThat(BaseUrl.of(" http://localhost:8080/x/ ").value()).isEqualTo("http://localhost:8080/x/");
    }

    @Test
    void resolvesPagesBelowIt() {
        assertThat(BaseUrl.of("https://acme.github.io/cache").resolve("api/A.html"))
                .isEqualTo("https://acme.github.io/cache/api/A.html");
    }

    @Test
    void rejectsAnythingButAnAbsoluteHttpUrl() {
        for (String invalid : new String[] {"", "acme.github.io/cache", "/cache/", "ftp://x.org/", "https://x.org/?q=1", "https://x.org/#top", "https://exa mple.org"}) {
            assertThatThrownBy(() -> BaseUrl.of(invalid)).as(invalid).isInstanceOf(DoctizeException.class).hasMessageContaining("base URL");
        }
    }
}
