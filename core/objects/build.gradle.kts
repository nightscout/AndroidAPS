import kotlin.math.min

plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    kotlin("plugin.allopen")
    // Metro, a Kotlin compiler plugin - no KSP, no generated sources. The comment below was written
    // when this module's DI had to live in :app because Dagger cannot run in a multiplatform module.
    alias(libs.plugins.metro)
}

// Restated from all-open-dependencies, which applies com.android.library and cannot be used here.
allOpen {
    annotation("app.aaps.annotations.OpenForTesting")
}

kotlin {
    android {
        namespace = "app.aaps.core.objects"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
        // This module owns no resources, but its tests read R classes from :core:interfaces and
        // :core:ui through :shared:tests. Without this the R jars never reach the test classpath and
        // every test touching one dies with NoClassDefFoundError.
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:data"))
                api(project(":core:interfaces"))
                api(project(":core:keys"))
                api(project(":core:utils"))
            }
        }
        androidMain {
            dependencies {
                api(libs.kotlin.stdlib.jdk8)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":shared:impl"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.net.danlew.android.joda)
                implementation(libs.org.skyscreamer.jsonassert)
                // The platform org.json on the Android unit-test classpath is a stub.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

// :shared:tests and :shared:impl still carry the five product flavours. A multiplatform module asks
// for none, so Gradle cannot choose a variant - pin the test classpaths to `full`.
listOf("androidHostTestCompileClasspath", "androidHostTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(com.android.build.api.attributes.ProductFlavorAttr.of("standard"), objects.named("full"))
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
