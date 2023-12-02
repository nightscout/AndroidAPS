plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.configuration"
}


dependencies {
    implementation(project(":database:entities"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))

    //WorkManager
    api(Libs.AndroidX.Work.runtimeKtx)
    // Maintenance
    api(Libs.AndroidX.gridLayout)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}