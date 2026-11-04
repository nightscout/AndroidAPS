plugins {
    id("kmp-android-library")
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "app.aaps.workflow"
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // The calculation chain is the point of this module and it is plain Kotlin, so it belongs in
    // commonMain. Only the Android executor binding with its wake lock stays in androidMain.
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:utils"))
            }
        }

        // Tests of commonMain classes belong here, not in androidHostTest: that source set runs on the
        // JVM only, so the executor that ships to iOS would be verified on Android alone. Mockito is
        // JVM only, so tests here use hand written fakes.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        // Hand written rather than taken from test-module-dependencies, because that applies
        // com.android.library and so cannot be used by a multiplatform module. Same approach as
        // :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))

                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures in TestBaseWithProfile.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
