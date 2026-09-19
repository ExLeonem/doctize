plugins {
    id("doctize.java-conventions")
    id("doctize.generate-docs")
}

dependencies {
    compileOnly(project(":doctize-annotations"))
    annotationProcessor(project(":doctize-processor"))
}

// The site is generated with: ./gradlew :doctize-example:generateDocs

// Generating the documentation is part of `check`, and it must be free of warnings: a broken {@link} fails the build.
tasks.named<JavaExec>("generateDocs") { args("--fail-on-warnings") }
tasks.named("check") { dependsOn("generateDocs") }
