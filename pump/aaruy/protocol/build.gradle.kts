plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.aaruy.protocol"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(libs.androidx.annotation)
    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")

    // The vendor library is not in the app any more. It stays as the reference for AarDifferentialTest.
    testImplementation(files("libs/mylibrary-release.aar"))
    testImplementation("no.nordicsemi.android:ble:2.7.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.1")
    testImplementation(project(":shared:tests"))
}
