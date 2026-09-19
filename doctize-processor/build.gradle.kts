plugins {
    id("doctize.java-conventions")
}

dependencies {
    implementation(project(":doctize-annotations"))
    implementation(project(":doctize-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
