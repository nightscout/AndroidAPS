plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlinx-serialization")
    id("android-module-dependencies")
}
android {
    namespace = "com.nightscout.eversense"
}
dependencies {
    api(libs.androidx.core)
    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)
    api(libs.org.slf4j.api)
    api(libs.com.github.tony19.logback.android)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
}