plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.shared.impl"
    defaultConfig {
        minSdk = Versions.wearMinSdk  // for wear
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))

    //Logger
    api(libs.org.slf4j.api)
    api(libs.com.github.tony19.logback.android)

    api(libs.com.caverock.androidsvg)

    api(libs.io.reactivex.rxjava3.rxandroid)
    api(libs.net.danlew.android.joda)

    api(libs.com.google.dagger.android.support)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}