plugins {
    alias(libs.plugins.android.library)
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
    implementation(project(":core:interfaces"))
    implementation(project(":core:libraries"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    api(Libs.AndroidX.constraintLayout)
    api(Libs.AndroidX.fragment)
    api(Libs.AndroidX.navigationFragment)
    api(libs.com.google.android.material)

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}