package dev.doctize;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.model.SourceLocation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceLinksTest {

    SourceLocation location = new SourceLocation("core/src/main/java/com/acme/Cache.java", 42);

    @Test
    void eachForgeGetsItsOwnAddressForALineInAFile() {
        assertThat(link("https://github.com/acme/cache", "main"))
                .hasValue("https://github.com/acme/cache/blob/main/core/src/main/java/com/acme/Cache.java#L42");
        assertThat(link("https://gitlab.com/acme/cache", "main"))
                .hasValue("https://gitlab.com/acme/cache/-/blob/main/core/src/main/java/com/acme/Cache.java#L42");
        assertThat(link("https://bitbucket.org/acme/cache", "main"))
                .hasValue("https://bitbucket.org/acme/cache/src/main/core/src/main/java/com/acme/Cache.java#lines-42");
        assertThat(link("https://codeberg.org/acme/cache", "3f2a"))
                .hasValue("https://codeberg.org/acme/cache/src/commit/3f2a/core/src/main/java/com/acme/Cache.java#L42");
    }

    @Test
    void aConfiguredAddressWinsAndWorksOnAnyHost() {
        SourceLinks links = SourceLinks.of("https://git.acme.com/cache/-/blob/v2/{path}#L{line}", "", Optional.empty());

        assertThat(links.apply(location)).hasValue("https://git.acme.com/cache/-/blob/v2/core/src/main/java/com/acme/Cache.java#L42");
    }

    @Test
    void withoutARevisionAnUnknownHostOrALocationThereIsNoLink() {
        assertThat(SourceLinks.of("", "https://github.com/acme/cache", Optional.empty()).apply(location)).isEmpty();
        assertThat(link("https://git.acme.com/acme/cache", "main")).isEmpty();
        assertThat(SourceLinks.of("", "https://github.com/acme/cache", Optional.of("main")).apply(null)).isEmpty();
    }

    @Test
    void pathsAndBranchesAreEncodedButKeepTheirSlashes() {
        SourceLinks links = SourceLinks.of("", "https://github.com/acme/cache", Optional.of("feature/new cache"));

        assertThat(links.apply(new SourceLocation("my docs/Cache.java", 1)))
                .hasValue("https://github.com/acme/cache/blob/feature/new%20cache/my%20docs/Cache.java#L1");
    }

    private Optional<String> link(String repository, String revision) {
        return SourceLinks.of("", repository, Optional.of(revision)).apply(location);
    }
}
