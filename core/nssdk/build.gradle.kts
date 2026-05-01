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
    implementation(libs.com.squareup.retrofit2.converter.gson)
    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.okhttp3.logging.interceptor)
    api(libs.net.danlew.android.joda)

    api(libs.kotlin.stdlib.jdk8)

    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.rx3)
    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)
}