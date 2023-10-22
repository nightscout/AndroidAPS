plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.automation"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:main"))

    api(Libs.AndroidX.constraintLayout)
    api(Libs.Google.Android.PlayServices.location)
    api(Libs.Kotlin.reflect)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}