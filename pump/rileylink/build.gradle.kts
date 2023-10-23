plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.androidaps.plugins.pump.common.hw.rileylink"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":core:ui"))
    implementation(project(":pump:pump-common"))

    testImplementation(project(":shared:tests"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}
