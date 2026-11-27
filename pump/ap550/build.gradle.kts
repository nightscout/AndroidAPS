plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.ap550s"

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":database:impl"))

    // Vendor protocol SDK. `api` so the protocol types stay visible to consumers of this driver.
    api(files("libs/ap550-protocol-1.0.0.aar"))

    // QR scan: the camera/barcode code the AP550 QR screen needs is bundled in this module.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Pump history database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.rxjava3)
    ksp(libs.androidx.room.compiler)

    testImplementation(project(":shared:tests"))
}