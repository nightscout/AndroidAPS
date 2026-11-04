plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

registerKeyStrings("automation")

kotlin {
    android {
        namespace = "app.aaps.plugins.automation"
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Rule for moving to commonMain: the action or trigger class itself is shared, and only the call it
    // cannot make everywhere is lifted out behind an interface implemented per platform, as
    // PairedBtDevices does. ActionSendSMS needs nothing here - it already goes through the
    // SmsCommunicator interface.

    sourceSets {
        // The triggers, the actions and their screens live here. androidMain inherits all of this,
        // so nothing below is repeated there.
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:utils"))
                implementation(project(":core:ui"))

                api(libs.jetbrains.compose.runtime)
                api(libs.jetbrains.compose.foundation)
                api(libs.jetbrains.compose.ui)
                api(libs.jetbrains.compose.material3)
                api(libs.jetbrains.compose.material.icons.extended)
                // The JetBrains republish, not androidx.lifecycle: same `androidx.lifecycle.*` package
                // names, but with Apple targets. Same choice as :core:ui and :ui.
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                // A Compose Multiplatform library - it publishes iosArm64, jvm and wasm too, so the
                // reorderable list works everywhere and does not pin a screen to Android.
                implementation(libs.sh.calvin.reorderable)
                // The JetBrains republish of the Preview annotation - same package name, with iOS.
                implementation(libs.jetbrains.compose.ui.tooling.preview)
            }
        }

        iosTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        androidMain {
            dependencies {
                api(libs.com.google.android.gms.playservices.location)
                implementation(libs.kotlin.reflect)
                // OpenStreetMap for map picker
                implementation(libs.org.osmdroid)

                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.ui)
                api(libs.androidx.ui.graphics)
                api(libs.androidx.ui.tooling)
                api(libs.androidx.ui.tooling.preview)
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)
                api(libs.androidx.lifecycle.runtime.compose)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both apply com.android.library and so cannot be
        // used by a multiplatform module. Same approach as :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":shared:impl"))
                implementation(project(":implementation"))
                implementation(project(":plugins:main"))
                implementation(project(":pump:virtual"))

                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.org.robolectric)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures in TestBaseWithProfile.
                implementation(libs.org.json.android)
                implementation(libs.org.skyscreamer.jsonassert)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
