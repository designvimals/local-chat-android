plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appStringsWorkbook = rootProject.projectDir.resolve("../outputs/app_strings/app_strings.xlsx").normalize()
val generatedStringsXml = projectDir.resolve("src/main/res/values/strings.xml")
val pythonCommand = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "python" else "python3"

val syncAppStrings by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Generate Android strings.xml from the editable Excel catalog."
    workingDir(rootProject.projectDir.resolve(".."))
    commandLine(
        pythonCommand,
        "scripts/sync_android_strings.py",
        appStringsWorkbook.absolutePath,
        generatedStringsXml.absolutePath
    )
    inputs.file(appStringsWorkbook)
    outputs.file(generatedStringsXml)
}

tasks.named("preBuild").configure {
    dependsOn(syncAppStrings)
}

android {
    namespace = "com.example.privatevault"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.privatevault"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "0.3.0"

        val releaseRequested = gradle.startParameter.taskNames.any { task ->
            task.contains("Release", ignoreCase = true)
        }
        val configuredBackendUrl = providers.gradleProperty("pocBackendUrl").orNull
        val configuredRegistrationKey = providers.gradleProperty("pocRegistrationKey").orNull
        if (releaseRequested) {
            if (configuredBackendUrl.isNullOrBlank() || !configuredBackendUrl.startsWith("https://")) {
                throw GradleException("Release builds require -PpocBackendUrl with a public HTTPS relay URL.")
            }
            if (configuredRegistrationKey.isNullOrBlank() || configuredRegistrationKey == "local-dev-registration-key-change-me") {
                throw GradleException("Release builds require -PpocRegistrationKey matching the relay.")
            }
        }

        val backendUrl = configuredBackendUrl ?: "http://10.0.2.2:8787"
        val registrationKey = configuredRegistrationKey ?: "local-dev-registration-key-change-me"

        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "REGISTRATION_KEY", "\"$registrationKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    if (!releaseKeystorePath.isNullOrBlank()) {
        signingConfigs.create("githubRelease") {
            storeFile = file(releaseKeystorePath)
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("githubRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val ktorVersion = "3.4.3"

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
