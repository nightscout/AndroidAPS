plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.insulin"
}


dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:graph"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":core:objects"))

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}