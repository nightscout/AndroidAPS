/**
 * What every multiplatform library with an Android target has in common.
 *
 * The plain Android modules get their SDK levels and lint settings from `android-module-dependencies`.
 * A multiplatform module cannot apply that: it applies `com.android.library`, which AGP 9 refuses
 * together with the multiplatform plugin. So this is the same set for them, plus the targets.
 *
 * A module still sets its own `namespace`, and only what is really its own: `androidResources`,
 * `withHostTest`, a lower `minSdk` for the modules the watch uses, Compose, Metro.
 */
plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk

        // Same as android-module-dependencies. Without it MissingTranslation would switch on, and the
        // locale files that are empty today would fail a release build. The per locale report of
        // GenerateKeyStringsTask covers the same ground.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows. Only linking and running their tests need a Mac, and those
    // tasks report SKIPPED rather than failing. The targets are what make a java.* or Android import
    // in commonMain fail the build instead of quietly compiling.
    iosArm64()
    iosSimulatorArm64()

    // Desktop: Windows, macOS and Linux.
    jvm()
}
