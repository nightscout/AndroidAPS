plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.omnipod.emulator"
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":core:utils"))
    // The wire format: packets, messages, the pairing and session maths. The emulator is the pod side of the same code.
    implementation(project(":pump:omnipod:common"))

    testImplementation(project(":shared:tests"))
}
