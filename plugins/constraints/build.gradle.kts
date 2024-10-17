plugins {
    alias(libs.plugins.android.library)
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
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))

    testImplementation(project(":implementation"))
    testImplementation(project(":pump:insight"))
    testImplementation(project(":plugins:aps"))
    testImplementation(project(":plugins:source"))
    testImplementation(project(":pump:dana"))
    testImplementation(project(":pump:danar"))
    testImplementation(project(":pump:danars"))
    testImplementation(project(":pump:virtual"))
    testImplementation(project(":shared:tests"))

    // Phone checker
    api(libs.com.scottyab.rootbeer.lib)

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}