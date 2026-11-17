plugins {
    id("kmp-android-library")
    // The emulated patch is saved for a test that kills the app.
    id("kotlinx-serialization")
}

// The emulated CareLevo patch: the protocol answered as a real patch does, and its radio on the Bluetooth v2
// EmulatedBleCentral. On every platform, so the driver can be run without a patch on iOS too.
kotlin {
    android {
        namespace = "app.aaps.pump.carelevo.emulator"
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:interfaces"))
                implementation(project(":pump:carelevo:protocol"))
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies com.android.library and so
        // cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
