plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
}

/**
 * The shared part of every app: navigation, the composable root and the start sequence.
 *
 * `:app` cannot be multiplatform - it is `com.android.application`, and it owns the product flavours
 * and the Android entry points. So the part of it that is the same on every platform lives here
 * instead. Android, iOS and the desktop all build on it.
 *
 * The plugin dependencies moved here from `:app` rather than being copied: they are `api`, so `:app`
 * still sees them for its DI graph. That keeps the number of edges in the build graph the same bar
 * one, which matters because every plugin is on this list.
 *
 * `:implementation` is here for `AppStartSequence`, which runs its migrations and its schedulers. The
 * plugins already depend on it, so this adds no new step to the build order.
 */
kotlin {
    android {
        namespace = "app.aaps.appshell"
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:data"))
                api(project(":core:interfaces"))
                api(project(":core:keys"))
                api(project(":core:objects"))
                api(project(":core:ui"))
                api(project(":ui"))
                implementation(project(":implementation"))

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
