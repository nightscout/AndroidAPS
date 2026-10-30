plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

registerKeyStrings("main")

kotlin {
    android {
        namespace = "app.aaps.plugins.main"
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
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))

                implementation(libs.androidx.collection)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jetbrains.compose.runtime)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :plugins:sensitivity.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":implementation"))
                implementation(project(":plugins:aps"))
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

