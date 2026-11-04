plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.aaruy.emulator"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":pump:aaruy:protocol"))

    testImplementation(project(":shared:tests"))
    // The vendor library is the reference for AarOnEmulatorTest. It needs the Nordic library, but the app does not.
    testImplementation(files("../protocol/libs/mylibrary-release.aar"))
    testImplementation("no.nordicsemi.android:ble:2.7.2")
    testImplementation("no.nordicsemi.android.support.v18:scanner:1.6.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.1")
}
