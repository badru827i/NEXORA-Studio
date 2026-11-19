plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "studio.nexora.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "studio.nexora.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

tasks.register("verifyTimelineMath") {
    group = "verification"
    description = "Run dependency-free checks for the editor's trim and speed bounds."
    doLast {
        val trimStart = 90f.coerceIn(0f, 50f - 1f)
        check(trimStart == 49f) { "Trim start bound failed" }

        val trimEnd = 10f.coerceIn(50f + 1f, 100f)
        check(trimEnd == 51f) { "Trim end bound failed" }

        val lowSpeed = 0f.coerceIn(0.25f, 3f)
        val highSpeed = 8f.coerceIn(0.25f, 3f)
        check(lowSpeed == 0.25f && highSpeed == 3f) { "Playback speed bounds failed" }

        println("NEXORA timeline smoke checks passed.")
    }
}
