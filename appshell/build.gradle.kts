plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
}

metro {
    interop {
        // The plugins this links against still carry javax annotations in places.
        includeDagger()
    }
}

/**
 * The app's shell: navigation and the composable root.
 *
 * `:app` cannot be multiplatform - it is `com.android.application`, and it owns the product flavours
 * and the Android entry points. So the part of it that is really just UI lives here instead, in a
 * module that can become multiplatform.
 *
 * The plugin dependencies moved here from `:app` rather than being copied: they are `api`, so `:app`
 * still sees them for its DI graph. That keeps the number of edges in the build graph the same bar
 * one, which matters because every plugin is on this list.
 *
 * Everything is in **androidMain** for now. The screens the navigation graph routes to
 * (`SetupWizardScreen`, `AutomationRuntime`, ...) are still in their plugins' androidMain, and
 * `:plugins:sync` is not multiplatform at all yet, so there is nothing to gain from an iOS target
 * here today. When those move, this module gains `iosArm64()`/`iosSimulatorArm64()` and the files
 * move to commonMain - the source set is already named for it.
 */
kotlin {
    android {
        namespace = "app.aaps.appshell"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
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

    sourceSets {
        androidMain {
            dependencies {
                api(project(":core:data"))
                api(project(":core:interfaces"))
                api(project(":core:keys"))
                api(project(":core:objects"))
                api(project(":core:ui"))
                api(project(":ui"))

                // Feature plugins self-register into the plugin map. This list moved here from `:app`
                // with the navigation graph that routes to them; `api` so `:app` still builds its
                // graph against them. Adding or removing a plugin stays an edit in settings.gradle.
                rootProject.subprojects
                    .filter { it.path.startsWith(":plugins:") && it.buildFile.exists() }
                    .forEach { api(project(it.path)) }

                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.runtime)
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.navigation)
                api(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.activity.compose)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

