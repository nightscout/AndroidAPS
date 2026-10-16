plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.common"
}

dependencies {
    implementation(project(":core:keys"))
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))

    // XStream is gone: PumpSyncStorage was its only user in the whole repo, and it needed
    // AnyTypePermission.ANY to work, which switches off the type allowlist entirely.
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.com.google.code.gson)

}
