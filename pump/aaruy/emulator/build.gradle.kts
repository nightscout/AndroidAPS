plugins {
    id("kmp-android-library")
    // The emulated pump is saved for a test that kills the app.
    id("kotlinx-serialization")
}

// The emulated Aaruy pump: the protocol answered as a real pump does, and its radio on the Bluetooth v2
// EmulatedBleCentral. On every platform, so the driver can be run without a pump on iOS too.
kotlin {
    android {
        namespace = "app.aaps.pump.aaruy.emulator"
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:interfaces"))
                implementation(project(":pump:aaruy:protocol"))
                // The pump's own AES, apart from the client's.
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies com.android.library and so
        // cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
