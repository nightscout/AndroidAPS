plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
}

// The convention plugins are gone with the flip. Only `test-module-dependencies` mattered here and
// it contributed nothing: every line in it is testImplementation or androidTestImplementation, and
// this module has no tests of its own - it is the fixtures other modules test with.
//
// No Dagger KSP either. Nothing here needs a generated factory; the one `@Inject` left is an
// annotation nobody processes. Verified by deleting `shared/tests/build` and rebuilding.

kotlin {
    android {
        namespace = "app.aaps.shared.tests"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Declared so an Android import cannot quietly reach shared code. Very little can live in
    // commonMain here, and the reason is worth writing down: JUnit 5, Mockito and RxJava are all
    // JVM-only, so anything built on them - TestBase, TestBaseWithProfile, TestAapsSchedulers - is
    // Android by nature, not by accident. The same goes for the generated `*StringIds` maps that
    // TextRefStubs needs, which only exist on Android.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
            }
        }

        getByName("androidMain") {
            dependencies {
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":implementation"))
                implementation(project(":plugins:aps"))
                implementation(project(":shared:impl"))

                // api, not implementation: every consumer writes its tests against these.
                api(libs.org.mockito.junit.jupiter)
                api(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.kotlin)
            }
        }
    }
}
