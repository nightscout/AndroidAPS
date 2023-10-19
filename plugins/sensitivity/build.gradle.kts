plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
}

apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

android {
    namespace = "app.aaps.plugins.sensitivity"
}


dependencies {
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}