plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.main"
}

dependencies {
    implementation(project(":database:entities"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))

    api(Libs.Kotlin.stdlibJdk8)
    api(Libs.Google.Android.material)
    api(Libs.Google.guava)
    api(Libs.AndroidX.activity)
    api(Libs.AndroidX.appCompat)

    api(Libs.Dagger.android)
    api(Libs.Dagger.androidSupport)

    //WorkManager
    api(Libs.AndroidX.Work.runtimeKtx)  // DataWorkerStorage

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}