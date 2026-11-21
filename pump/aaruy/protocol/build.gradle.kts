plugins {
    id("kmp-android-library")
}

// The Aaruy wire protocol: the encrypted frames, the packets and the client that sends them over the shared Bluetooth
// v2 central. No platform code, so the driver and the emulator both sit on it, on every platform.
kotlin {
    android {
        namespace = "app.aaps.pump.aaruy.protocol"
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:interfaces"))
                // AES of the frames, the same on every platform.
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
            }
        }

        // Runs on every target: the frames must have the same bytes on iOS as on Android.
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
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
