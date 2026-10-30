plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules and the other converted plugins.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro, so this module can wire its own Android entry points.
    alias(libs.plugins.metro)
}

// Same generator as the other converted plugins, pointed at this module's strings.
val generateApsStrings = registerKeyStrings("aps")

kotlin {
    android {
        namespace = "app.aaps.plugins.aps"
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

    // Desktop (Windows/macOS/Linux). Compose Multiplatform resolves its `desktop` variant from a
    // plain jvm() target, so no special target name is needed.
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateApsStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:nssdk"))
                implementation(project(":core:objects"))
                implementation(project(":core:utils"))
                implementation(project(":core:ui"))

                implementation(libs.androidx.collection)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jetbrains.compose.runtime)
                api(kotlin("reflect"))
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateApsStrings.flatMap { it.androidOutputDir })
            dependencies {
                implementation(project(":core:graph"))
                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.androidx.work.runtime)
                implementation(libs.org.slf4j.api)
                // APS (it should be androidTestImplementation but it doesn't work)
                runtimeOnly(libs.org.mozilla.rhino)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :plugins:main.
        // Tests of commonMain classes belong here, not in androidHostTest: that source set runs on the
        // JVM only, so code that ships to iOS would be verified on Android alone. Mockito is JVM
        // only, so anything moved here uses hand written fakes instead.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":pump:virtual"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // Compose UI tests (AutotuneScreenTest). Restated from compose-test-module-dependencies,
                // which applies com.android.library and so cannot be used here.
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                implementation(libs.org.robolectric)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.mozilla.rhino)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

