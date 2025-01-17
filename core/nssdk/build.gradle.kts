plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.nssdk"
}

dependencies {
    api(libs.com.squareup.retrofit2.retrofit)
    api(libs.com.squareup.retrofit2.adapter.rxjava3)
    api(libs.com.squareup.retrofit2.converter.gson)
    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.okhttp3.logging.interceptor)
    api(libs.com.google.code.gson)
    api(libs.net.danlew.android.joda)
    api(libs.io.reactivex.rxjava3.rxkotlin)

    api(libs.androidx.core)
    api(libs.kotlin.stdlib.jdk8)

    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.rx3)
    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.protobuf)
}