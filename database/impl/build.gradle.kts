plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    kotlin("plugin.allopen")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.database.impl"

    defaultConfig {
        ksp {
            arg("room.incremental", "true")
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

dependencies {
    api(libs.kotlin.stdlib.jdk8)
    api(libs.kotlin.reflect)
    api(libs.androidx.core)

    api(libs.io.reactivex.rxjava3.rxandroid)
    api(libs.io.reactivex.rxjava3.rxkotlin)

    api(libs.com.google.code.gson)

    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)

    androidTestImplementation(libs.androidx.room.testing)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.androidx.room.compiler)
}