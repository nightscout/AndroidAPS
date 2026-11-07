plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as :plugins:source and the :core modules.
    alias(libs.plugins.android.kmp.library)
    // No KSP and no Metro: this driver has no dependency injection at all. It is reached through
    // the OnePlusCgmDrivers object from :plugins:source, not from the application graph.
}

kotlin {
    android {
        namespace = "app.aaps.plugins.dexcomoneplus"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        // Creates the androidHostTest compilation. isReturnDefaultValues and
        // isIncludeAndroidResources are restated from test-module-dependencies, which a
        // multiplatform module cannot apply: the scanner test touches android.bluetooth classes
        // that are stubs on the unit-test classpath.
        withHostTest {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
        // Restated from android-module-dependencies, which a multiplatform module cannot apply.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Android is the only target. The protocol code is shared (commonMain, no Android imports) so
    // another platform can be added later; nothing checks that yet, so keep Android types out by hand.

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:interfaces"))
                // The xDrip KEKS authentication library, translated to Kotlin Multiplatform, so
                // OnePlusSessionAuthKeks and OnePlusKeksGuideCerts can live in commonMain.
                implementation(project(":plugins:libkeks"))
            }
        }

        androidMain {
            dependencies {
                // AAPS writes everything that comes through slf4j to AndroidAPS.log. JVM only,
                // which is why OnePlusLog keeps its sink behind an expect/actual.
                implementation(libs.org.slf4j.api)
            }
        }

        // Shared tests (kotlin.test only) run in testAndroidHostTest. Everything else stays in
        // androidHostTest, written against JUnit 5 and Truth.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used by a multiplatform module.
        getByName("androidHostTest") {
            dependencies {
                // Only what the test classes actually import: JUnit 5 and Truth. Mockito,
                // joda-time, jsonassert and coroutines-test came from the convention plugin and
                // are not used here.
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.com.google.truth)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
