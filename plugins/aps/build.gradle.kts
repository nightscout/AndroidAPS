plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.aps"
}

dependencies {
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":core:main"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":pump:virtual"))
    testImplementation(project(":shared:tests"))

    api(Libs.AndroidX.appCompat)
    api(Libs.AndroidX.swipeRefreshLayout)
    api(Libs.AndroidX.gridLayout)

    // APS
    api(Libs.Mozilla.rhino)

    kapt(Libs.Dagger.androidProcessor)
}