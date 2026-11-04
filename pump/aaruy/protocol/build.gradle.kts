plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.aaruy.protocol"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")

    // The vendor library is not in the app any more. It stays as the reference for AarDifferentialTest.
    testImplementation(files("libs/mylibrary-release.aar"))
    testImplementation("no.nordicsemi.android:ble:2.7.2")
    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)
    testImplementation(project(":shared:tests"))
}
