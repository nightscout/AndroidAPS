plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.main"
}

dependencies {
    implementation(project(":shared:impl"))
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))

    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:insulin"))
    testImplementation(project(":shared:tests"))

    api(Libs.AndroidX.appCompat)
    api(Libs.Google.Android.material)

    // Actions
    api(Libs.AndroidX.gridLayout)

    //SmsCommunicator
    api(Libs.javaOtp)
    api(Libs.qrGen)

    // Overview
    api(Libs.Google.Android.flexbox)

    // Food
    api(Libs.AndroidX.Work.runtimeKtx)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}