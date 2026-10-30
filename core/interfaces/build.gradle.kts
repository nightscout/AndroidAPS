import kotlin.math.min

plugins {
    id("kmp-android-library")
    alias(libs.plugins.metro)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("kotlinx-serialization")
}

// Lets the data enums here (ConcentrationType, InsulinType, CwfMetadataKey) carry a TextRef instead
// of an R.string Int.
registerKeyStrings("interfaces")

kotlin {
    android {
        namespace = "app.aaps.core.interfaces"
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Off by default for a multiplatform library, unlike a plain android library.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
    }

    // Android and the JVM desktop target share their actuals: a ReentrantLock and Dispatchers.IO are
    // the same answer on both, and duplicating them would be two files that must not drift. Only what
    // is genuinely Android - the runtime Bluetooth permissions - stays in androidMain.
    //
    // Applied explicitly, because the manual dependsOn below would otherwise switch the default
    // hierarchy off - which silently unwires iosMain and breaks every Apple actual.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain {
            dependencies {
                api(project(":core:data"))
                api(project(":core:keys"))

                // project.dependencies.platform, because a Kotlin source set dependency block has no
                // platform() of its own.
                api(project.dependencies.platform(libs.kotlinx.serialization.bom))
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.serialization.protobuf)
                api(libs.kotlinx.datetime)
                api(project.dependencies.platform(libs.kotlinx.coroutines.bom))
                api(libs.kotlinx.coroutines.core)
                // Multiplatform since 1.4.0, so LongSparseArray is usable from common code.
                // AutosensDataStore, TddCalculator and TirCalculator all expose it, so it stays api.
                api(libs.androidx.collection)
                // The CMP runtime, so the compose compiler plugin has something to compile against on
                // every target. On Android CMP delegates to androidx, so the composeBom still decides
                // the Android versions and nothing about the Android build changes.
                api(libs.jetbrains.compose.runtime)
                // ImageVector and friends live here, not in the runtime. Several interfaces in this
                // module carry an icon, so without this they could not be common.
                api(libs.jetbrains.compose.ui)
            }
        }

        // The iOS notification delegate is the one thing here with logic worth testing: it owns the
        // single delegate slot iOS gives an app, and routing between handlers is easy to get wrong.
        iosTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        androidMain {
            dependencies {
                // The 41 consumer modules resolve these transitively, so they must stay `api`. They
                // are Android or JVM only, which is why they belong to this source set rather than
                // commonMain.

                // Dependency Injection

                api(libs.androidx.appcompat)
                api(libs.androidx.compose.ui)
                api(libs.androidx.documentfile)

                api(libs.org.apache.commons.lang3)
                api(libs.net.danlew.android.joda)

                //RxBus / RxJava base
                api(libs.io.reactivex.rxjava3.rxkotlin)
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Hand written rather than taken from test-module-dependencies, because that convention
        // plugin applies com.android.library and so cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.com.google.truth)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.io.reactivex.rxjava3.rxandroid)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
