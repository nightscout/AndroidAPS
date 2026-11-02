import kotlin.math.min

plugins {
    id("kmp-android-library")
}

registerKeyStrings("keys")

kotlin {
    // The Android half. The strings stay in src/androidMain/res and keep being processed by AAPT,
    // which is the whole point: Android goes on resolving locales the way it always has, including
    // the always English lookup the search index needs.
    android {
        namespace = "app.aaps.core.keys"
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Off by default for a multiplatform library, unlike a plain android library.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
    }

    sourceSets {
        commonMain {
            dependencies {
                // project.dependencies.platform, because a Kotlin source set dependency block has
                // no platform() of its own.
                api(project.dependencies.platform(libs.kotlinx.coroutines.bom))
                api(libs.kotlinx.coroutines.core)
            }
        }
        // Hand written rather than taken from test-module-dependencies, because that convention
        // plugin applies com.android.library and so cannot be used here. Only what the one test
        // in this module actually needs.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.com.google.truth)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
