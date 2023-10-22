plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.androidaps.plugins.pump.eopatch"
    buildFeatures {
        dataBinding = true
    }
}

dependencies {
    implementation(project(":pump:eopatch-core"))
    implementation(project(":core:libraries"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":database:entities"))

    api(Libs.Google.guava)

    //RxAndroidBle
    api(Libs.rxandroidBle)
    api(Libs.rx3ReplayingShare)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}