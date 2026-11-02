import kotlin.math.min

plugins {
    id("kmp-android-library")
    // The Compose COMPILER, which ships with Kotlin and compiles @Composable for every target.
    alias(libs.plugins.compose.compiler)
    // The Compose Multiplatform framework. The compiler plugin above is applied per project rather
    // than per target, so every target needs a Compose runtime on its class path.
    alias(libs.plugins.compose.multiplatform)
}

registerKeyStrings("coreUi")

kotlin {
    android {
        namespace = "app.aaps.core.ui"
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Off by default for a multiplatform library, unlike a plain android library. This module is
        // where most of the app's strings, drawables and raw alarm sounds live, so it must be on.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        // isIncludeAndroidResources is what makes Robolectric work: the Compose UI tests here need a
        // real merged resource table and the manifest that holds the activity createComposeRule()
        // launches.
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
                api(libs.kotlinx.datetime)

                // CMP rather than androidx. On Android CMP delegates to androidx, so the composeBom
                // below still decides the Android versions and nothing about the Android build
                // changes.
                api(libs.jetbrains.compose.runtime)
                api(libs.jetbrains.compose.foundation)
                api(libs.jetbrains.compose.ui)
                // Replaces the deprecated androidx.compose.ui.backhandler.BackHandler.
                api(libs.jetbrains.androidx.navigationevent.compose)
                api(libs.jetbrains.compose.material3)
                api(libs.jetbrains.compose.material.icons.extended)
                // viewModel() for Compose. The JetBrains republish, not androidx: it is the one with Apple
                // targets, and `metroViewModel()` below has to compile wherever the shared UI does.
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                // Metro's view model extension. `api` so every module with a view model gets @ViewModelKey
                // without repeating the dependency - there are eighty of them to convert.
                api(libs.metrox.viewmodel)
                implementation(libs.jetbrains.compose.ui.tooling.preview)
            }
        }

        androidMain {
            dependencies {
                // Everything here was `api` on the old android library and the consumer modules
                // resolve it transitively, so it must stay exported.
                api(libs.androidx.appcompat)
                api(libs.com.google.android.material)
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)
                api(libs.androidx.compose.runtime)
                api(libs.androidx.activity.compose)
                api(libs.androidx.lifecycle.runtime.compose)


                implementation(libs.androidx.compose.ui.tooling.preview)
                // Was debugImplementation. The AGP multiplatform library target has no build types,
                // so there is no debug-only configuration to put it in. It only matters for rendering
                // @Preview, and R8 in the app module drops it from a release build.
                implementation(libs.androidx.compose.ui.tooling)
            }
        }

        // Shared tests for shared code. `ClockPattern` moved to commonMain when the desktop target
        // started using it, so its tests belong here rather than in `iosTest` - otherwise the reader
        // would only be covered on one of the targets that uses it.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both convention plugins apply
        // com.android.library and so cannot be used here.
        //
        // createComposeRule() is a JUnit4 TestRule and RobolectricTestRunner is a JUnit4 runner, so
        // these tests run JUnit4 style; the vintage engine bridges them onto the JUnit Platform.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                // Was debugImplementation: supplies the manifest holding the activity that
                // createComposeRule() launches.
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
