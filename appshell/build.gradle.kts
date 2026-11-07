plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
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
 * `AppRoute` and `AppNavGraph` are both in **commonMain**, and every module this one depends on
 * already has Apple targets. Three things still have to happen before this module can declare its
 * own, and they are all inside `AppNavGraph`:
 *
 *  1. **Seven `R.string` references**, aliased in from `:plugins:main` and `:core:ui`. Both modules
 *     already generate `TextRef`s, so this is the usual name-preserving swap.
 *  2. **`backStackEntry.arguments?.getString(...)`** - `arguments` is a `Bundle` on Android and a
 *     `SavedState` in multiplatform navigation, so those reads need the `SavedState` API.
 *  3. A stale `androidx.compose.ui.res.stringResource` import.
 *
 * The navigation dependency is already the JetBrains republish, so nothing is waiting on that.
 * `:plugins:sync` is multiplatform now too - an older note here said otherwise.
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


    iosArm64()
    iosSimulatorArm64()

    // Desktop (Windows/macOS/Linux). Compose Multiplatform resolves its `desktop` variant from a
    // plain jvm() target, so no special target name is needed.
    jvm()

    sourceSets {
        commonMain {
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

                api(libs.jetbrains.compose.runtime)
                api(libs.jetbrains.compose.foundation)
                api(libs.jetbrains.compose.ui)
                api(libs.jetbrains.compose.material3)
                api(libs.jetbrains.compose.material.icons.extended)
                // The JetBrains republishes, not the plain androidx ones: same package names, with
                // Apple targets. Same choice as :core:ui and :ui.
                api(libs.jetbrains.androidx.compose.navigation)
                api(libs.jetbrains.lifecycle.runtime.compose)
            }
        }

        androidMain {
            dependencies {
                api(project.dependencies.platform(libs.androidx.compose.bom))
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
                // Compose UI tests on the JVM via Robolectric. Declared here rather than through
                // `compose-test-module-dependencies`, because that convention plugin applies
                // com.android.library, which AGP 9 refuses next to the multiplatform plugin.
                // createComposeRule() is a JUnit4 rule, so the vintage engine bridges these onto the
                // JUnit Platform beside the Jupiter tests.
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                // Supplies the manifest holding the activity createComposeRule() launches.
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
