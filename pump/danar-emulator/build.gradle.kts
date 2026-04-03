plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.danar.emulator"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:utils"))
    implementation(project(":pump:dana"))
    implementation(project(":pump:danar"))

    testImplementation(project(":shared:tests"))

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}
