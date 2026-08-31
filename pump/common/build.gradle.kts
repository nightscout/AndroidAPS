plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
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
    namespace = "app.aaps.pump.common"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))

    implementation(libs.com.thoughtworks.xstream)
    implementation(libs.com.google.code.gson)
    implementation(project(":core:keys"))

}
