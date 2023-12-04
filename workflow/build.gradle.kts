plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.workflow"
}

dependencies {
    implementation(project(":core:libraries"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":plugins:main"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}