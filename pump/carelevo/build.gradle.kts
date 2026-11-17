plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro builds this module's classes into the graph.
    alias(libs.plugins.metro)
    // The patch, infusion, settings and alarm records are stored as JSON.
    id("kotlinx-serialization")
}

registerKeyStrings("carelevo")

kotlin {
    android {
        namespace = "app.aaps.pump.carelevo"
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":pump:carelevo:protocol"))
                implementation(project(":pump:carelevo:emulator"))
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.material.icons.extended)
                implementation(libs.jetbrains.compose.ui.tooling.preview)
                // The multiplatform lifecycle: ViewModel, viewModelScope and collectAsStateWithLifecycle.
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)
            }
        }

        androidMain {
            dependencies {
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.ui)
                implementation(libs.androidx.compose.ui.tooling)
                implementation(libs.androidx.lifecycle.process)
            }
        }

        // Hand written rather than taken from test-module-dependencies and compose-test-module-dependencies, which
        // apply com.android.library and so cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                implementation(libs.org.robolectric)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
