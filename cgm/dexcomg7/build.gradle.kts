plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.cgm.dexcomg7"
}

// Dexcom G7 / ONE+ read straight over Bluetooth, as a BG source. Android only: the Bluetooth link, the
// camera for the applicator barcode, and the screens. The wire protocol is in :cgm:dexcomg7:protocol.
dependencies {
    api(project(":cgm:dexcomg7:protocol"))
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    // The readings list every BG source shows (view, remove, duplicates marked).
    implementation(project(":plugins:source"))

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.com.google.zxing.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(project(":shared:tests"))
}
