plugins {
    id("kmp-android-library")
    // Metro, only for its annotations: the fixtures here mock `Provider<T>` fields that production
    // constructors take, so the type has to be the same one production uses.
    alias(libs.plugins.metro)
}

// No withHostTest: this module has no tests of its own - it is the fixtures other modules test with.
kotlin {
    android {
        namespace = "app.aaps.shared.tests"
    }

    // Very little can live in commonMain here, and the reason is worth writing down: JUnit 5, Mockito
    // and RxJava are all JVM-only, so anything built on them - TestBase, TestBaseWithProfile,
    // TestAapsSchedulers - is Android by nature, not by accident. The same goes for the generated
    // `*StringIds` maps that TextRefStubs needs, which only exist on Android.
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
