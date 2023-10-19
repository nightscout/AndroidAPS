plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
}

apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

android {
    namespace = "app.aaps.plugins.smoothing"
}


dependencies {
    implementation(project(":database:entities"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:ui"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}