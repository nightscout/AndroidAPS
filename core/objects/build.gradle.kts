import kotlin.math.min

plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    kotlin("plugin.allopen")
    // Metro, a Kotlin compiler plugin - no KSP, no generated sources.
    alias(libs.plugins.metro)
}

// Restated from all-open-dependencies, which applies com.android.library and cannot be used here.
allOpen {
    annotation("app.aaps.annotations.OpenForTesting")
}

kotlin {
    android {
        namespace = "app.aaps.core.objects"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
        // This module owns no resources, but its tests read R classes from :core:interfaces and
        // :core:ui through :shared:tests. Without this the R jars never reach the test classpath and
        // every test touching one dies with NoClassDefFoundError.
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // Desktop (Windows/macOS/Linux). Compose Multiplatform resolves its `desktop` variant from a
    // plain jvm() target, so no special target name is needed.
    jvm()

    // CryptoUtil is plain javax.crypto with no Android in it, and its output is a STORED format, so
    // Android and desktop share the one implementation rather than keeping two that could drift.
    // Applied explicitly, because the manual dependsOn below would otherwise switch the automatic
    // hierarchy off and silently unwire iosMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain {
            dependencies {
                api(project(":core:data"))
                api(project(":core:interfaces"))
                api(project(":core:keys"))
                api(project(":core:utils"))
            }
        }
        androidMain {
            dependencies {
                api(libs.kotlin.stdlib.jdk8)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":shared:impl"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The platform org.json on the Android unit-test classpath is a stub.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
