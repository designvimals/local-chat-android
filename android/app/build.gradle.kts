plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.privatevault"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.privatevault"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        val backendUrl = providers.gradleProperty("pocBackendUrl").getOrElse("http://10.0.2.2:8787")
        val registrationKey = providers.gradleProperty("pocRegistrationKey").getOrElse("local-dev-registration-key-change-me")

        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "REGISTRATION_KEY", "\"$registrationKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

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
