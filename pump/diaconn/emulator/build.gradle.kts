plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.diaconn.emulator"
}

dependencies {
    implementation(project(":core:interfaces"))
}
