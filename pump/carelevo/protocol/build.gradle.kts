plugins {
    id("kmp-android-library")
    // ActiveAlarmSnapshotFlags is part of the emulator's saved patch.
    id("kotlinx-serialization")
}

// The CareLevo wire protocol: the command/response definitions the patch speaks, and the client contract they are
// sent over. No platform code and no driver internals, so the real driver and the emulator can both sit on it without
// either depending on the other, on every platform.
kotlin {
    android {
        namespace = "app.aaps.pump.carelevo.protocol"
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:interfaces"))
            }
        }

        // Runs on every target: the dose encoding must give the same bytes on iOS as on Android.
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies com.android.library and so
        // cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.com.google.truth)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
