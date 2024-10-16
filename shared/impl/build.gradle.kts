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
    api(Libs.Logging.slf4jApi)
    api(Libs.Logging.logbackAndroid)

    api(Libs.androidSvg)

    api(libs.io.reactivex.rxjava3.rxandroid)
    api(Libs.jodaTimeAndroid)

    api(libs.com.google.dagger.android.support)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}