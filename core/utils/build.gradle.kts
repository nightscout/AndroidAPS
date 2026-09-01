import kotlin.math.min

plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as :core:keys and :core:data.
    alias(libs.plugins.android.kmp.library)
}

// The first module SPLIT rather than converted whole. Most of what lives here is Android glue -
// WorkManager, Bluetooth, Intent/Bundle, Html - and only a handful of files are genuinely portable,
// so androidMain keeps the bulk and commonMain takes the four that carry no platform types.
//
// The `allopen` plugin is deliberately not carried over: it exists to open @OpenForTesting classes
// for mocking and nothing in this module is annotated with it.
kotlin {
    android {
        namespace = "app.aaps.core.utils"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
        // Restated from android-module-dependencies, which a multiplatform module cannot apply.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    jvm {
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.datetime)
                // JsonLenientRead reads kotlinx documents from common code. The androidMain block
                // below keeps its own `api` on the same artifact for the consumers that resolve it
                // transitively from there.
                api(project.dependencies.platform(libs.kotlinx.serialization.bom))
                api(libs.kotlinx.serialization.json)
            }
        }

        // CryptoUtil is plain javax.crypto and is shared with desktop, so its Base64 has to be on
        // the JVM classpath too. A Java jar, so the same artifact serves both.
        jvmMain {
            dependencies {
                api(libs.com.madgag.spongycastle)
            }
        }

        androidMain {
            dependencies {
                // Everything below was `api` on the old android library and several of the 24
                // consumer modules resolve these transitively, so they must stay exported. They are
                // JVM/Android only, which is exactly why they belong to this source set.
                api(libs.net.danlew.android.joda)
                api(project.dependencies.platform(libs.kotlinx.serialization.bom))
                api(libs.kotlinx.serialization.json)

                //Firebase
                api(project.dependencies.platform(libs.com.google.firebase.bom))
                api(libs.com.google.firebase.analytics)
                api(libs.com.google.firebase.crashlytics)

                //CryptoUtil
                api(libs.com.madgag.spongycastle)
                api(libs.com.google.crypto.tink)

                //WorkManager
                api(libs.androidx.work.runtime) // Inbox, WorkExtensions, WorkerDataBuilder

                // ProcessLifecycleOwner for DeferredForegroundStart
                implementation(libs.androidx.lifecycle.process)

            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :core:keys.
        // Tests of commonMain classes belong here: androidHostTest runs on the JVM only.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))

            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.com.google.truth)
                implementation(libs.org.mockito.kotlin)
                // JsonHelperTest needs a REAL org.json. On the Android unit-test classpath the
                // platform one is a stub that throws "not mocked"; test-module-dependencies used to
                // supply the real thing transitively through jsonassert, and this module can no
                // longer apply that plugin. This is AOSP's own implementation, repackaged.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
