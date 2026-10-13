import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.core.interfaces"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:keys"))


    api(libs.androidx.appcompat)
    api(libs.androidx.preference)

    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.protobuf)

    api(libs.org.apache.commons.lang3)
    api(libs.net.danlew.android.joda)

    //RxBus
    api(libs.io.reactivex.rxjava3.rxkotlin)
    testImplementation(libs.io.reactivex.rxjava3.rxandroid)
}