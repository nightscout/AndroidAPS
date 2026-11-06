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

    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)
    testImplementation(project(":shared:tests"))
}
