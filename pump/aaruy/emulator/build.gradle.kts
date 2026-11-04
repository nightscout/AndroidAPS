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

    testImplementation(project(":shared:tests"))
}
