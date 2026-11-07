plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.aaruy"

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":pump:aaruy:protocol"))
    implementation(project(":pump:aaruy:emulator"))
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":pump:common"))
    implementation(project(":shared:impl"))

    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")
    implementation(libs.com.google.code.gson)

    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(project(":shared:tests"))
    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)
}
