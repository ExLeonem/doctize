plugins {
    id("doctize.java-conventions")
    id("doctize.publishing-conventions")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

description = "The annotations that mark Java types and packages for documentation with Doctize."
