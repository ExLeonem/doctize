plugins {
    id("doctize.java-conventions")
    id("doctize.publishing-conventions")
}

description = "An annotation processor that extracts the Javadoc of Doctize-annotated elements at compile time."

dependencies {
    implementation(project(":doctize-annotations"))
    implementation(project(":doctize-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
