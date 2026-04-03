plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.common.hw.rileylink"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:keys"))

    testImplementation(project(":shared:tests"))

    implementation(libs.androidx.hilt.navigation.compose)

    api(libs.net.danlew.android.joda)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}
