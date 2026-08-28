plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules and :pump:virtual.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro is a Kotlin COMPILER plugin, not KSP. It is pinned to the Kotlin version, so the first
    // question is simply whether a build exists for the Kotlin this repo uses.
    alias(libs.plugins.metro)
}

// Same generator as :core:ui and :pump:virtual, pointed at this module's strings. The strings
// themselves do not move, and AAPT keeps resolving them on Android exactly as before - this only adds
// a TextRef-named view of them that common code can reach.
val generateSmoothingStrings = tasks.register<GenerateKeyStringsTask>("generateSmoothingStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.smoothing")
    owner.set("smoothing")
    objectName.set("SmoothingStrings")
    idsObjectName.set("SmoothingStringIds")
    reportFile.set(layout.buildDirectory.file("reports/smoothingStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory derives its convention from the task name, so both
    // properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/smoothingStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/smoothingStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.plugins.smoothing"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
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

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSmoothingStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:ui"))

                implementation(libs.cmp.runtime)
                implementation(libs.cmp.material.icons.extended)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateSmoothingStrings.flatMap { it.androidOutputDir })
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :pump:virtual.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures. Same reason as :pump:virtual.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

