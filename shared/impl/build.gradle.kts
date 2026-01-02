import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.shared.impl"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:utils"))

    //Logger
    api(libs.org.slf4j.api)
    api(libs.com.github.tony19.logback.android)

    api(libs.com.caverock.androidsvg)

    api(libs.io.reactivex.rxjava3.rxandroid)
    api(libs.net.danlew.android.joda)

    api(libs.com.google.dagger.android.support)
    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}