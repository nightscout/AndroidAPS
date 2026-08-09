import kotlin.math.min

plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin:
    // "The 'com.android.library' (or 'com.android.application') plugin is not compatible with the
    // 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0."
    alias(libs.plugins.android.kmp.library)
}

// One task, not one per variant: a multiplatform module has no product flavours, and a Kotlin
// source set takes a task provider directly, so the Android variant API is not needed here.
val generateKeyStrings = tasks.register<GenerateKeyStringsTask>("generateKeyStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.core.keys")
    objectName.set("KeysStrings")
    idsObjectName.set("KeysStringIds")
    reportFile.set(layout.buildDirectory.file("reports/keyStrings/translations.txt"))
    commonOutputDir.set(layout.buildDirectory.dir("generated/keyStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/keyStrings/android"))
}

kotlin {
    // The Android half. The strings stay in src/androidMain/res and keep being processed by AAPT,
    // which is the whole point: Android goes on resolving locales the way it always has, including
    // the always English lookup the search index needs.
    android {
        namespace = "app.aaps.core.keys"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Off by default for a multiplatform library, unlike a plain android library.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply. Without
        // it MissingTranslation would switch on for the first time here, and the 19 locale files
        // that are empty today would fail a release build. The generator's per locale report covers
        // the same ground and is on by default, so nothing is actually lost by keeping this off.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Desktop JVM, for a client on Windows.
    jvm {
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
    }

    // Declared unconditionally. Kotlin/Native cross compiles klibs for Apple targets from any host
    // since 2.2.20, so these really do compile on Windows - only linking, cinterop and running
    // tests need a Mac, and those tasks report SKIPPED rather than failing.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            // Platform neutral: 327 string names as TextRef, no Android types.
            kotlin.srcDir(generateKeyStrings.flatMap { it.commonOutputDir })
            dependencies {
                // project.dependencies.platform, because a Kotlin source set dependency block has
                // no platform() of its own.
                api(project.dependencies.platform(libs.kotlinx.coroutines.bom))
                api(libs.kotlinx.coroutines.core)
            }
        }
        androidMain {
            // Android only: the name to R.string id map.
            kotlin.srcDir(generateKeyStrings.flatMap { it.androidOutputDir })
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

tasks.withType<Test> {
    useJUnitPlatform()
}
