plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.carelevo.emulator"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":pump:carelevo"))

    testImplementation(project(":shared:tests"))
}
