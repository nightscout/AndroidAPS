plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
}

apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

android {

    buildFeatures {
        aidl = true
    }
    namespace = "info.nightscout.pump.combo"
}

dependencies {
    implementation(project(":core:libraries"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))

    // RuffyScripter
    api(Libs.Google.guava)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}