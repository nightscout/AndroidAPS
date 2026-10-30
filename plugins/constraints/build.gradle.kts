plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
    // SntpClient is @OpenForTesting and its test mocks it, which needs the class to be open in a
    // release build without opening it in the source.
    id("all-open-dependencies")
}

// The three qualified plugin buckets are keyed by @AllConfigs, @APS and @NotNSClient, each carrying
// Metro's @Qualifier - ConstraintsBucketsTest is the guard.

registerKeyStrings("constraints")

kotlin {
    android {
        namespace = "app.aaps.plugins.constraints"
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Android and desktop share what is plain JVM - the SNTP client. Applied explicitly, because
    // the manual dependsOn below would otherwise switch the automatic hierarchy off and silently
    // unwire iosMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        // What ConstraintsCheckerImpl needs. androidMain inherits these, so the rest of the module
        // keeps compiling unchanged; only the modules no common file uses yet stay android only.
        commonMain {
            dependencies {
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
            }
        }

        androidMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:utils"))

                api(libs.kotlinx.datetime)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both apply com.android.library and so cannot be
        // used by a multiplatform module. Same approach as :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":implementation"))
                implementation(project(":plugins:aps"))
                implementation(project(":plugins:source"))
                implementation(project(":pump:virtual"))
                implementation(project(":shared:impl"))
                implementation(project(":shared:tests"))

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
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
