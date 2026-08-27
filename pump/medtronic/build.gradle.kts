plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

metro {
    interop {
        // The classes here keep their javax annotations; interop is what lets Metro read them.
        includeDagger()
    }
}

android {
    namespace = "app.aaps.pump.medtronic"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":pump:common"))
    implementation(project(":pump:rileylink"))

    testImplementation(project(":core:keys"))
    testImplementation(project(":shared:tests"))

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
}
