plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.aaps.pump.virtual"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":implementation"))

    testImplementation(project(":shared:tests"))

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)

    // compose dependencies
    api(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))

    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.compose.material3)

}