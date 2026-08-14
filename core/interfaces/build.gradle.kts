import kotlin.math.min

plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin:
    // "The 'com.android.library' (or 'com.android.application') plugin is not compatible with the
    // 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0."
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
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

    // Only the Android target for now. Not because of the source split - see below.
    //
    // The compose compiler plugin is applied per project, not per target, and it fails ANY
    // compilation that has no Compose runtime on the class path - a plain jvm() target as much as an
    // Apple one. This module needs that plugin for exactly one declaration:
    // `UserEntryPresentationHelper.iconColor` is `@Composable`. Every other Compose reference here is
    // a plain type (ImageVector, Color, AnnotatedString) and needs only the dependency.
    //
    // So the way to open the other targets is to move that one interface to :core:ui - both of its
    // consumers, :implementation and :ui, already depend on it - and drop the plugin from here. That
    // touches another module's API, so it is deliberately left as its own change rather than folded
    // into the source split.
    //
    // The split itself was done against the Apple compiler: every file in commonMain was placed by
    // compiling for iosArm64 and moving whatever failed, so commonMain is platform neutral as of this
    // commit. What is missing without that target is the *enforcement* - a java.* import added to
    // commonMain later would compile fine on Android and nothing would object.

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
