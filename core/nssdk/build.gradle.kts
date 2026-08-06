plugins {
    alias(libs.plugins.android.library)
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.nssdk"
}

dependencies {
    api(libs.com.squareup.okhttp3.okhttp)

    // Ktor on the OkHttp engine: reuses the OkHttp already in the app rather than adding a second
    // HTTP stack. The engine becomes Darwin when this module builds for iOS.
    api(libs.io.ktor.client.core)
    implementation(libs.io.ktor.client.okhttp)
    implementation(libs.io.ktor.client.content.negotiation)
    implementation(libs.io.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.datetime)

    // Test only: a real HTTP server on localhost, so the same characterization tests run against
    // Retrofit today and Ktor after the port. Ktor MockEngine could not do that - it only exists
    // after the swap, so it could never pin the OLD behaviour.
    testImplementation(libs.com.squareup.okhttp3.mockwebserver)

    api(libs.kotlin.stdlib.jdk8)

    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.android)
    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)
}