plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.carelevo.protocol"
}

// The CareLevo wire protocol: the command/response definitions the patch speaks, and the transport
// contract they are sent over. No Android and no driver internals, so both the real driver and the
// emulator can sit on it without either depending on the other.
dependencies {
    implementation(project(":core:interfaces"))

    testImplementation(project(":shared:tests"))
}
