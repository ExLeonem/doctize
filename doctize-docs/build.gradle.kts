plugins {
    id("doctize.java-conventions")
    id("doctize.generate-docs")
}

// Doctize documents itself. The processor cannot run inside the modules it is built from (it depends on
// them), so this module compiles their sources a second time, with the annotation processor enabled. Only
// the extracted documentation is used; the compiled classes are not published.
sourceSets.main {
    java.srcDir("../doctize-annotations/src/main/java")
    java.srcDir("../doctize-core/src/main/java")
}

dependencies {
    // The libraries the sources of doctize-core need to compile.
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.yaml)
    compileOnly(libs.commonmark)
    compileOnly(libs.commonmark.tables)
    compileOnly(libs.commonmark.strikethrough)
    compileOnly(libs.commonmark.alerts)
    compileOnly(libs.commonmark.autolink)
    compileOnly(libs.commonmark.tasklist)
    compileOnly(libs.jmustache)

    annotationProcessor(project(":doctize-processor"))
}

// The site is generated with: ./gradlew :doctize-docs:generateDocs
