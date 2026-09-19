plugins {
    id("doctize.java-conventions")
    application
}

dependencies {
    implementation(project(":doctize-core"))
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dev.doctize.cli.DoctizeCli"
    applicationName = "doctize"
}
