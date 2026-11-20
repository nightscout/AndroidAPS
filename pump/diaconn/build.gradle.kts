plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.pump.diaconn"
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":pump:common"))
    // The in-tree emulator, picked by the `emulate_diaconn` option (DiaconnTransportBindings).
    implementation(project(":pump:diaconn:emulator"))
    implementation(project(":shared:impl"))

    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.retrofit2.retrofit)
    implementation(libs.com.squareup.retrofit2.converter.gson)

    testImplementation(project(":shared:tests"))
    // The shared Bluetooth transport contract runs on Robolectric, a JUnit 4 runner.
    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)

}