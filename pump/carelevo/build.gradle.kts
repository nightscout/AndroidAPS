plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    // The patch, infusion, settings and alarm records are stored as JSON.
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.carelevo"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":pump:carelevo:protocol"))
    implementation(project(":pump:carelevo:emulator"))
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:keys"))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.process)
}
