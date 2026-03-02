plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.core.graph"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.ui)
    api(libs.com.patrykandpatrick.vico.compose)
    api(libs.com.patrykandpatrick.vico.compose.m3)
}
