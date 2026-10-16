plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.cgm.dexcomg7.core"
}

// The Dexcom G7 / ONE+ connection without any screens: the Bluetooth link, pairing, the session that
// collects readings, and the saved sensor state. Kept apart from :cgm:dexcomg7 so the watch app can
// run the same connection in the sensor's watch slot. Needs nothing the watch app does not have.
dependencies {
    api(project(":cgm:dexcomg7:protocol"))
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))

    testImplementation(project(":shared:tests"))
}
