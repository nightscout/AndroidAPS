plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.androidaps.plugins.pump.omnipod.common"
}

dependencies {
    implementation(project(":core:libraries"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    api(Libs.AndroidX.constraintLayout)
    api(Libs.AndroidX.fragment)
    api(Libs.AndroidX.navigationFragment)
    api(Libs.Google.Android.material)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}