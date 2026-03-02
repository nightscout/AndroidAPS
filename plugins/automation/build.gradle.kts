plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.aaps.plugins.automation"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:main"))
    testImplementation(project(":pump:virtual"))

    api(libs.androidx.constraintlayout)
    api(libs.com.google.android.gms.playservices.location)
    api(libs.kotlin.reflect)
    // OpenStreetMap for map picker
    api(libs.org.osmdroid)

    // Compose dependencies
    api(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.lifecycle.runtime.compose)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}