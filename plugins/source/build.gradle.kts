plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.source"
}


dependencies {
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":shared:impl"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(Libs.AndroidX.Work.testing)

    testImplementation(project(":shared:tests"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}