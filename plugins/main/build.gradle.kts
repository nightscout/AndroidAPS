plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.main"
    buildFeatures {
        compose = true
    }
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
    testImplementation(project(":shared:tests"))

    api(libs.androidx.appcompat)
    api(libs.com.google.android.material)

    // Compose
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Actions
    api(libs.androidx.gridlayout)

    api(libs.kotlinx.datetime)

    // Overview
    api(libs.com.google.android.flexbox)

    // Food
    api(libs.androidx.work.runtime)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}