plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.source"

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
    implementation(project(":ui"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.androidx.work.testing)
    // Robolectric for the InstaraStaleCheckWorker test: it needs a real Android Context
    // (WorkManager.getInstance + Intent broadcasts). Vintage engine bridges its JUnit4 runner
    // onto the JUnit Platform alongside the existing Jupiter tests.
    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)

    testImplementation(project(":shared:tests"))


}