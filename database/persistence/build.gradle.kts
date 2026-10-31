plugins {
    id("kmp-android-library")
    // Metro is the only DI framework here: PersistenceLayerImpl is a @ContributesBinding.
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "app.aaps.database.persistence"
        // Creates the androidHostTest compilation, which also pulls in commonTest.
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
                implementation(project(":database:impl"))
            }
        }

        // Hand written rather than taken from test-module-dependencies, because that convention
        // plugin applies com.android.library and so cannot be used here.
        // Tests of commonMain classes belong here, not in androidHostTest: that source set runs on the
        // JVM only, so code that ships to iOS would be verified on Android alone.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
