plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    // Metro is the only DI framework here now: AppRepository comes from DatabaseBindings.
    alias(libs.plugins.metro)
    id("kotlinx-serialization")
}

ksp {
    arg("room.incremental", "true")
    // Straight into the instrumented test assets. MigrationTestHelper opens the schema JSONs from
    // assets, and a multiplatform module has no android.sourceSets block to add a directory to - the
    // only asset source it reads is src/<sourceSet>/assets. The other Room modules are ordinary
    // Android libraries and keep $projectDir/schemas.
    arg("room.schemaLocation", "$projectDir/src/androidDeviceTest/assets")
}

kotlin {
    android {
        namespace = "app.aaps.database.impl"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        // Needed for the schema JSONs under src/androidDeviceTest/assets to be packaged: with resources
        // off, the multiplatform android plugin skips the asset merge entirely.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        // Restated from test-module-dependencies, which this module can no longer apply.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        // The instrumented Room tests. Their source set is src/androidDeviceTest and the task is
        // connectedAndroidDeviceTest - .circleci/config.yml knows about both.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows; linking and running still need a Mac. Room only runs its
    // processor for Android here (see kspAndroid below) - the entities and DAOs in commonMain need the
    // annotations to resolve, not the generated implementation, and the @Database class that does need
    // it stays in androidMain with Converters.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.datetime)
                api(project.dependencies.platform(libs.kotlinx.serialization.bom))
                api(libs.kotlinx.serialization.json)
                api(libs.androidx.room.runtime)
            }
        }

        androidMain {
            dependencies {
                api(libs.kotlin.stdlib.jdk8)
                api(libs.kotlin.reflect)
                api(libs.kotlinx.datetime)

                api(libs.com.google.code.gson)

                api(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        // Hand written rather than taken from test-module-dependencies, because that convention
        // plugin applies com.android.library and so cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // Brings a real org.json. The android.jar stub returns null from JSONObject.put, and
                // the APS result transactions build their JSON in the test. test-module-dependencies
                // used to pull this in for every module; this one needs it.
                implementation(libs.org.skyscreamer.jsonassert)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.ext)
                implementation(libs.androidx.test.rules)
                implementation(libs.com.google.truth)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.room.testing)
            }
        }
    }
}

dependencies {
    // Room generates the database initialiser per target, so its processor runs for every target the
    // @Database class is compiled for.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
