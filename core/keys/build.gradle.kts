plugins {
    id("com.android.library")
    id("android-module-dependencies")
    id("kotlin-kapt")
}

android {
    namespace = "app.aaps.core.keys"
    defaultConfig {
        minSdk = Versions.wearMinSdk  // for wear
    }
}
dependencies {

    api(Libs.AndroidX.preference)
    api(Libs.Dagger.android)
    api(Libs.Dagger.androidSupport)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}