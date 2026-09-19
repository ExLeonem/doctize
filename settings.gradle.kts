plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "doctize"

include(
    "doctize-annotations",
    "doctize-core",
    "doctize-processor",
    "doctize-cli",
    "doctize-docs",
    "doctize-example",
    "doctize-tests",
)
