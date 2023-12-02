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
    namespace = "app.aaps.plugins.constraints"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":database:entities"))

    testImplementation(project(":database:impl"))
    testImplementation(project(":implementation"))
    testImplementation(project(":insight"))
    testImplementation(project(":plugins:aps"))
    testImplementation(project(":plugins:source"))
    testImplementation(project(":pump:combo"))
    testImplementation(project(":pump:dana"))
    testImplementation(project(":pump:danar"))
    testImplementation(project(":pump:danars"))
    testImplementation(project(":pump:virtual"))
    testImplementation(project(":shared:tests"))

    // Phone checker
    api(Libs.rootBeer)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}