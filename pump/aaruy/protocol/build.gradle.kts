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

    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)
    testImplementation(project(":shared:tests"))
}
