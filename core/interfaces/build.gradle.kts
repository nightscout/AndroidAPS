import kotlin.math.min

plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin:
    // "The 'com.android.library' (or 'com.android.application') plugin is not compatible with the
    // 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0."
    alias(libs.plugins.android.kmp.library)
    // The Compose COMPILER, which ships with Kotlin and compiles @Composable for every target.
    alias(libs.plugins.compose.compiler)
    // The Compose Multiplatform framework. Needed because the compiler plugin above is applied per
    // project rather than per target and fails any compilation with no Compose runtime on the class
    // path - which is what kept the Apple targets out of this module at first.
    alias(libs.plugins.compose.multiplatform)
    id("kotlinx-serialization")
}

// One task, not one per variant. A multiplatform module has no product flavours, and a Kotlin source
// set takes a task provider directly, so the Android variant API this used to go through is not
// needed. Same generator as :core:keys and :core:ui, pointed at this module's strings: it lets the
// data enums here (ConcentrationType, InsulinType, CwfMetadataKey) carry a TextRef instead of an
// R.string Int. The strings themselves do not move, and AAPT keeps resolving them as before.
val generateInterfacesStrings = tasks.register<GenerateKeyStringsTask>("generateInterfacesStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.core.interfaces")
    owner.set("interfaces")
    objectName.set("InterfacesStrings")
    idsObjectName.set("InterfacesStringIds")
    reportFile.set(layout.buildDirectory.file("reports/interfacesStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only applies a convention derived from the task
    // name, so both properties would land on one directory and the second file written would delete
    // the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/interfacesStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/interfacesStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.core.interfaces"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Off by default for a multiplatform library, unlike a plain android library.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        withHostTest { }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply. Without
        // it MissingTranslation would switch on for the first time here and the locale files that are
        // empty today would fail a release build.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows. Linking and running still need a Mac, and those tasks
    // report SKIPPED rather than failing.
    //
    // These are what keep commonMain honest. The split was made by compiling for iosArm64 and moving
    // whatever failed, so keeping the target means a java.* import added to commonMain later fails
    // the build instead of quietly compiling on Android.
    //
    // Deliberately no jvm() target: it pulls in the desktop Compose surface (skiko-awt) and gives the
    // module another way to fail without saying anything about iOS. Recorded in wave 17 of
    // _docs/KMP_IOS_FEASIBILITY.md.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateInterfacesStrings.flatMap { it.commonOutputDir })
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
                implementation(libs.cmp.runtime)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateInterfacesStrings.flatMap { it.androidOutputDir })
            dependencies {
                // Everything here was `api` on the old android library and the 41 consumer modules
                // resolve these transitively, so they must stay exported. They are Android or JVM
                // only, which is exactly why they belong to this source set rather than commonMain.

                // Dependency Injection
                api(libs.com.google.dagger.android)
                api(libs.com.google.dagger.hilt.android)

                api(libs.androidx.appcompat)
                api(libs.androidx.compose.ui)
                api(libs.androidx.documentfile)

                api(libs.org.apache.commons.lang3)
                api(libs.net.danlew.android.joda)

                //RxBus / RxJava base
                api(libs.io.reactivex.rxjava3.rxkotlin)
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

tasks.withType<Test> {
    useJUnitPlatform()
}
