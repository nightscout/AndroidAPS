plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}
metro {
    interop {
        // Reads the javax and Dagger annotations still on this module, so a class only moves its
        // wiring, not its annotations.
        includeDagger()
    }
}

android {
    namespace = "app.aaps.database.persistence"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":database:impl"))
}