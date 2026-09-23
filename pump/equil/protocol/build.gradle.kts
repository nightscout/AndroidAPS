plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.equil.protocol"
}

// The Equil wire protocol: packet framing, the 0.00625 U/step encoding, CRC, AES pairing crypto, and
// the transport contract they travel over. No Android and no driver internals, so both the real driver
// and the emulator sit on it without either depending on the other.
dependencies {
    implementation(project(":core:interfaces"))

    testImplementation(project(":shared:tests"))
}
