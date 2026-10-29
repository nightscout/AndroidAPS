plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.cgm.dexcomg7.protocol"
}

// The Dexcom G7 / ONE+ wire protocol: message formats, the pairing handshake and its cryptography,
// the pairing planner and the sensor lifecycle rules. No Android and no Bluetooth, so all of it can be
// tested on the JVM against a simulated sensor. Ported from Trio's G7SensorKit (MIT, LoopKit Authors).
dependencies {
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}
