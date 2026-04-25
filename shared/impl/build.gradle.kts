import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
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
    implementation(libs.org.slf4j.api)
    runtimeOnly(libs.com.github.tony19.logback.android)

    implementation(libs.com.caverock.androidsvg)

    implementation(libs.io.reactivex.rxjava3.rxandroid)
    runtimeOnly(libs.net.danlew.android.joda)

    api(libs.com.google.dagger.android.support)
    api(libs.com.google.dagger.hilt.android)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}