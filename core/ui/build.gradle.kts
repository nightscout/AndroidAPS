import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
}

android {
    namespace = "app.aaps.core.ui"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(libs.androidx.core)
    api(libs.androidx.appcompat)
    api(libs.androidx.preference)
    api(libs.androidx.gridlayout)

    api(libs.com.google.android.material)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)

    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
