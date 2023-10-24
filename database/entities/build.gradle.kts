plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.database.entities"
    defaultConfig {
        minSdk = Versions.wearMinSdk  // for wear
    }
}

dependencies {
    api(Libs.Kotlin.stdlibJdk8)
    api(Libs.AndroidX.core)
    api(Libs.AndroidX.Room.room)

    kapt(Libs.AndroidX.Room.compiler)
}