plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    // Metro is the only DI framework here: PersistenceLayerImpl is a @ContributesBinding.
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "app.aaps.database.persistence"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        // Restated from test-module-dependencies, which this module can no longer apply.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows. The converters are the whole module apart from
    // PersistenceLayerImpl, and they only map database entities onto core data, so they belong here.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":database:impl"))
            }
        }

        // PersistenceLayerImpl stays Android only: it takes AppRepository, which owns the Room
        // database and so cannot leave androidMain until Converters stops using Gson.
        androidMain { }

        // Hand written rather than taken from test-module-dependencies, because that convention
        // plugin applies com.android.library and so cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.com.google.truth)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}
