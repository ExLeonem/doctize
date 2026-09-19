/*
 * Release tasks. The published modules stage their artifacts in build/staging-deploy (see
 * doctize.publishing-conventions); this file turns that directory into a Maven Central bundle and uploads it.
 *
 *   ./gradlew centralBundle           builds build/central/doctize-<version>-central-bundle.zip
 *   ./gradlew publishToCentralPortal  uploads it (needs CENTRAL_PORTAL_USERNAME and CENTRAL_PORTAL_PASSWORD)
 */
val publishedModules = listOf("doctize-annotations", "doctize-processor", "doctize-core", "doctize-cli")

val cleanStaging by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("staging-deploy"))
}

val stageArtifacts by tasks.registering {
    group = "publishing"
    description = "Publishes all modules to build/staging-deploy, signed if a signing key is configured."
    dependsOn(cleanStaging)
    publishedModules.forEach { module ->
        dependsOn(":$module:publishAllPublicationsToStagingRepository")
        // The constraint belongs on the tasks that write to the directory, not on the aggregate that depends on them:
        // with parallel execution, publishing must never overlap with the clean.
        project(":$module").tasks.matching { it.name.endsWith("ToStagingRepository") }
            .configureEach { mustRunAfter(cleanStaging) }
    }
}

val centralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Zips the staged artifacts into the bundle that Maven Central's Portal accepts."
    dependsOn(stageArtifacts)
    from(layout.buildDirectory.dir("staging-deploy"))
    archiveFileName = "doctize-${project.version}-central-bundle.zip"
    destinationDirectory = layout.buildDirectory.dir("central")
}

tasks.register<Exec>("publishToCentralPortal") {
    group = "publishing"
    description = "Uploads the bundle to the Maven Central Portal. -PcentralPublishingType=AUTOMATIC releases it immediately."
    dependsOn(centralBundle)

    val bundle = centralBundle.flatMap { it.archiveFile }
    val publishingType = providers.gradleProperty("centralPublishingType").orElse("USER_MANAGED")
    val bundleName = "doctize-${project.version}"
    // The credentials are read from the environment by the shell, so they never appear in Gradle's output.
    environment("CENTRAL_BUNDLE", bundle.get().asFile.path)
    environment("CENTRAL_NAME", bundleName)
    environment("CENTRAL_TYPE", publishingType.get())
    commandLine(
        "sh", "-c",
        """
        test -n "${'$'}CENTRAL_PORTAL_USERNAME" && test -n "${'$'}CENTRAL_PORTAL_PASSWORD" || { echo "Set CENTRAL_PORTAL_USERNAME and CENTRAL_PORTAL_PASSWORD" >&2; exit 1; }
        token=${'$'}(printf '%s:%s' "${'$'}CENTRAL_PORTAL_USERNAME" "${'$'}CENTRAL_PORTAL_PASSWORD" | base64 | tr -d '\n')
        curl --fail-with-body --silent --show-error --request POST \
          --header "Authorization: Bearer ${'$'}token" \
          --form "bundle=@${'$'}CENTRAL_BUNDLE" \
          "https://central.sonatype.com/api/v1/publisher/upload?name=${'$'}CENTRAL_NAME&publishingType=${'$'}CENTRAL_TYPE"
        echo
        """.trimIndent(),
    )
}
