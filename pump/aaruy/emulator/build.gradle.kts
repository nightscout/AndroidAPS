plugins {
    alias(libs.plugins.android.library)
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
    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)
}
