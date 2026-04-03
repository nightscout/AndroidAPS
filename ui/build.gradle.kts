plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.ui"

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
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))

    api(libs.com.google.android.material)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.hilt.navigation.compose)
    api(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.sh.calvin.reorderable)
    api(libs.androidx.core)
    api(libs.kotlinx.datetime)

    api(libs.com.squareup.okhttp3.okhttp)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}