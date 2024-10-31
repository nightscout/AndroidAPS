plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.main"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:graph"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":shared:impl"))

    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:aps"))
    testImplementation(project(":plugins:insulin"))
    testImplementation(project(":shared:tests"))

    api(libs.androidx.appcompat)
    api(libs.com.google.android.material)

    // Actions
    api(libs.androidx.gridlayout)

    //SmsCommunicator
    api(libs.com.eatthepath.java.otp)
    api(libs.com.github.kenglxn.qrgen.android)

    // Overview
    api(libs.com.google.android.flexbox)

    // Food
    api(libs.androidx.work.runtime)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}