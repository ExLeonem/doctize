plugins {
    id("doctize.java-conventions")
    id("doctize.publishing-conventions")
}

description = "Ready-made themes for Doctize sites, chosen with theme.name. Optional: put it on the class path of the generator to use them."

dependencies {
    implementation(project(":doctize-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
