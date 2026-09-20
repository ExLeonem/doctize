package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PackagePatternTest {

    @ParameterizedTest
    @CsvSource({
        "com.acme.api, com.acme.api, true",
        "com.acme.api, com.acme.api.internal, false",
        "com.acme.*, com.acme.api, true",
        "com.acme.*, com.acme, false",
        "com.acme.*, com.acme.api.internal, false",
        "com.acme.api.**, com.acme.api, true",
        "com.acme.api.**, com.acme.api.internal.deep, true",
        "com.acme.api.**, com.acme.apix, false",
        "**.internal, internal, true",
        "**.internal, com.acme.internal, true",
        "**.internal, com.acme.internal.x, false",
        "com.**.internal, com.acme.deep.internal, true",
        "com.**.internal, com.internal, true",
        "com.acme.api*, com.acme.apix, true",
        "com.acme.*api, com.acme.myapi, true",
        "**, com.anything.at.all, true",
    })
    void matchesPackagesTheWayTheDocumentationSays(String pattern, String packageName, boolean expected) {
        assertThat(PackagePattern.matches(pattern, packageName)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void theDefaultPackageIsMatchedByDoubleStarOnly() {
        assertThat(PackagePattern.matches("**", "")).isTrue();
        assertThat(PackagePattern.matches("*", "")).isFalse();
    }
}
