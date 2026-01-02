plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.shared.tests"
    defaultConfig {
        minSdk = Versions.minSdk
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:objects"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":implementation"))
    implementation(project(":plugins:aps"))
    implementation(project(":shared:impl"))


    api(libs.org.mockito.junit.jupiter)
    api(libs.org.mockito.kotlin)
    api(libs.org.junit.jupiter.api)

    ksp(libs.com.google.dagger.compiler)
}