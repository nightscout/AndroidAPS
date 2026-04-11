plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.configuration"

    buildFeatures {
        compose = true
    }
}


dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":shared:impl"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":implementation"))

    //WorkManager
    api(libs.androidx.work.runtime)
    // Maintenance
    api(libs.androidx.gridlayout)

    // HTTP client for Google Drive API
    implementation(libs.com.squareup.okhttp3.okhttp)

    // Chrome Custom Tabs for OAuth flow
    api(libs.androidx.browser)

    // Compose
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.com.google.dagger.hilt.android)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}