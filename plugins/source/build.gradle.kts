plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

registerKeyStrings("source")

kotlin {
    android {
        namespace = "app.aaps.plugins.source"
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
        // InstaraStaleCheckWorker's test needs a real Context for WorkManager and Intent broadcasts.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))
                // :ui is multiplatform now, so the shared screens it hosts (ContentContainer) reach here.
                implementation(project(":ui"))

                api(libs.jetbrains.compose.runtime)
                api(libs.jetbrains.compose.foundation)
                api(libs.jetbrains.compose.ui)
                api(libs.jetbrains.compose.material3)
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
            }
        }

        androidMain {
            dependencies {
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.compose.runtime)
                // The native Libre 3 / Libre 3 Plus driver. Android only, like the plugin that uses it.
                implementation(project(":plugins:libre3"))
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.work.runtime)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both apply com.android.library and so cannot be
        // used by a multiplatform module. Same approach as :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                // InstaraWorkerTest uses kotlin.test assertions; test-module-dependencies used to supply this.
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.work.testing)
                // Robolectric for the InstaraStaleCheckWorker test: it needs a real Android Context
                // (WorkManager.getInstance + Intent broadcasts). The vintage engine bridges its JUnit4
                // runner onto the JUnit Platform alongside the Jupiter tests.
                implementation(libs.org.robolectric)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather than
                // throwing, which NPEs the shared profile fixtures in TestBaseWithProfile.
                implementation(libs.org.json.android)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
