plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
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
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

dependencies {
    api(libs.kotlin.stdlib.jdk8)
    api(libs.kotlin.reflect)

    api(libs.io.reactivex.rxjava3.rxkotlin) // RxJava base for room-rxjava3

    api(libs.com.google.code.gson)

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)
    api(libs.com.google.dagger.hilt.android)

    androidTestImplementation(libs.androidx.room.testing)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.androidx.room.compiler)
}