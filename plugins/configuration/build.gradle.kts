plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

metro {
    interop {
        // Metro reads javax.inject and Dagger annotations, so classes in this module do not have to be
        // rewritten to Metro's own annotations before a Metro graph can build them. Without this,
        // Metro ignores javax qualifiers and matches on type alone, which fails silently.
        includeDagger()
    }
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
    implementation(project(":core:ui"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":implementation"))

    // Compose
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.com.google.dagger.hilt.android)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}