plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.implementation"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":plugins:aps"))
    testImplementation(project(":pump:virtual"))

    // Protection
    api(Libs.AndroidX.biometric)
    //Logger
    api(Libs.Logging.slf4jApi)
    api(Libs.Logging.logbackAndroid)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}