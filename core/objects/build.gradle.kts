plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.objects"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))

    api(libs.kotlin.stdlib.jdk8)
    api(libs.com.google.android.material)
    api(libs.com.google.guava)
    api(Libs.AndroidX.activity)
    api(Libs.AndroidX.appCompat)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)

    //WorkManager
    api(Libs.AndroidX.Work.runtimeKtx)  // DataWorkerStorage

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}