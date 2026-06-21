plugins {
    id(MyPlugins.kotlin)
    id(Plugins.Kotlin.serialization)
    application
}

dependencies {
    // Project modules
    implementation(project(":downloader:core"))
    implementation(project(":downloader:monitor"))
    implementation(project(":shared:utils"))
    implementation(project(":shared:config"))
    implementation(project(":shared:nanohttp4k"))
    implementation(project(":integration:server"))

    // Kotlin
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.koin.core)

    // HTTP
    implementation(libs.okhttp.okhttp) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.okhttp.coroutines)

    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("com.github.ajalt.mordant:mordant:2.7.2")

    // Arrow
    implementation(libs.arrow.core)

    // Compose runtime (needed for monitor module's @Immutable annotations)
    implementation(libs.compose.runtime)

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass = "com.abdownloadmanager.cli.CliMainKt"
    applicationName = "abdm"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}