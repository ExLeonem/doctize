/*
 * The vendor's side of licensing: creating the signing key and issuing licenses. It is not published, so the code that
 * can sign never ships to customers; doctize-core only has the public key and can only verify.
 *
 *   ./gradlew :doctize-licensing:run --args="keygen --out signing-key.pem --public-out vendor-public-key.pem"
 *   ./gradlew :doctize-licensing:run --args="issue --key signing-key.pem --licensee 'Acme Corp' --expires 2027-12-31"
 */
plugins {
    id("doctize.java-conventions")
    application
}

description = "Issues Doctize licenses. Internal to the vendor; not published."

dependencies {
    api(project(":doctize-core"))
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dev.doctize.licensing.LicenseTool"
    applicationName = "doctize-license"
}
