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
    implementation(libs.com.squareup.retrofit2.retrofit)
    // Official Retrofit 3 artifact, same group and version as the Gson converter it replaces.
    // Goes away with Retrofit itself when the client moves to Ktor.
    implementation(libs.com.squareup.retrofit2.converter.kotlinx.serialization)
    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.okhttp3.logging.interceptor)
    api(libs.kotlinx.datetime)

    api(libs.kotlin.stdlib.jdk8)

    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.android)
    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)
}