plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.omnipod.eros"
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
    implementation(project(":pump:omnipod:common"))
    implementation(project(":pump:common"))
    implementation(project(":pump:rileylink"))

    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.rxjava3)

    implementation(libs.kotlinx.coroutines.rx3)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(project(":shared:tests"))
    // optional - Test helpers
    testImplementation(project(":implementation"))
    testImplementation(project(":shared:impl"))
    testImplementation(project(":shared:tests"))


    ksp(libs.androidx.room.compiler)
}
