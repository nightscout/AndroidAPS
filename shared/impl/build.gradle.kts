import kotlin.math.min

plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    id("kmp-test-defaults")
}

kotlin {
    android {
        namespace = "app.aaps.shared.impl"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // shared with :wear
        // Off by default for a multiplatform library. This module owns the watchface view key strings
        // and a manifest <queries> block, so it must be on.
        androidResources { enable = true }
        // Restated from test-module-dependencies: without isReturnDefaultValues the android.text stubs
        // throw instead of returning defaults, and without isIncludeAndroidResources the R jars never
        // reach the test classpath.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows; linking and running still need a Mac. Keeping the targets
    // is what stops an Android import from quietly reaching commonMain later.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
            }
        }

        androidMain {
            dependencies {
                implementation(project(":core:utils"))

                //Logger
                implementation(libs.org.slf4j.api)
                runtimeOnly(libs.com.github.tony19.logback.android)

                implementation(libs.com.caverock.androidsvg)

                implementation(libs.io.reactivex.rxjava3.rxandroid)
                runtimeOnly(libs.net.danlew.android.joda)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used by a multiplatform module.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.joda.time)
                implementation(libs.org.apache.commons.lang3)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
