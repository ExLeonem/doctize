plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "doctize"

include(
    "doctize-annotations",
    "doctize-core",
    "doctize-themes",
    "doctize-processor",
    "doctize-cli",
    "doctize-gradle-plugin",
    "doctize-docs",
    "doctize-example",
    "doctize-tests",
)
