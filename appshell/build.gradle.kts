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
 * `AppRoute` is already in **commonMain**. `AppNavGraph` is not, and exactly two things hold it
 * there:
 *
 *  1. **The setup wizard** - `SetupWizardScreen` and `SWDefinition` in `:plugins:configuration`.
 *     The screens themselves are nearly portable (one `BackHandler`), but `SWDefinition` still
 *     reaches for `FileListProvider.listPreferenceFiles`, `ResourceHelper.gs(Int)`, `AapsSchedulers`
 *     and `CryptoUtil.checkPassword`. That is a chain of small ports, not a move.
 *  2. **The permissions sheet** - `PluginPermissions` works in Android permission groups and takes a
 *     `Context`, and the graph reads `navController.context` for it. iOS has no equivalent model, so
 *     this one is expected to stay behind a port rather than move.
 *
 * A third, smaller step comes with them: `androidx.navigation:navigation-compose` has to become the
 * JetBrains republish (`org.jetbrains.androidx.navigation`), the same swap already done for
 * `navigationevent`. The import names do not change.
 *
 * When those are done this module gains its Apple targets and `AppNavGraph` moves too.
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

