plugins {
    id("doctize.java-conventions")
    id("doctize.generate-docs")
}

dependencies {
    compileOnly(project(":doctize-annotations"))
    annotationProcessor(project(":doctize-processor"))
}

// The site is generated with: ./gradlew :doctize-example:generateDocs
