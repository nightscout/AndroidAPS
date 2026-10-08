plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    // Robolectric plus the compose-ui-test artifacts, so the pairing wizard and the alert screen
    // can be driven on the JVM. Paired with jacoco-module-dependencies below, which is what makes
    // the Compose classes Robolectric loads show up in coverage.
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.pump.insight"
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    testImplementation(project(":shared:tests"))

    implementation(libs.com.google.android.material)
    api(libs.androidx.room.runtime)

    ksp(libs.androidx.room.compiler)
}