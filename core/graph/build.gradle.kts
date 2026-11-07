import kotlin.math.min

plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "app.aaps.core.graph"
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
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
                api(project(":core:data"))
                api(project(":core:interfaces"))
                api(project(":core:ui"))

                api(libs.jetbrains.compose.runtime)
                api(libs.jetbrains.compose.foundation)
                api(libs.jetbrains.compose.ui)
                api(libs.jetbrains.compose.material3)
                // Vico publishes Apple targets of its own, so the charts are shared rather than
                // reimplemented. This is the only third-party UI library in commonMain.
                api(libs.com.patrykandpatrick.vico.compose)
                implementation(libs.jetbrains.compose.ui.tooling.preview)
            }
        }

        androidMain {
            dependencies {

                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.runtime)
                api(libs.androidx.ui)

                implementation(libs.androidx.compose.ui.tooling.preview)
                // Was debugImplementation; the multiplatform library target has no build types.
                implementation(libs.androidx.compose.ui.tooling)
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
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
