plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules and the other converted plugins.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
    // Restated from all-open-dependencies, which applies com.android.library and so cannot be used
    // here. SntpClient is @OpenForTesting and its test mocks it, which needs the class to be open in
    // a release build without opening it in the source.
    kotlin("plugin.allopen")
}

allOpen {
    annotation("app.aaps.annotations.OpenForTesting")
}

// No `metro { interop { includeDagger() } }`: nothing here carries a javax annotation any more. The
// three qualified plugin buckets keep working because @AllConfigs, @APS and @NotNSClient each carry
// Metro's own @Qualifier alongside the javax one - ConstraintsBucketsTest is the guard.

kotlin {
    android {
        namespace = "app.aaps.plugins.constraints"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
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

    // Keeping the targets is what stops an android-only import from quietly reaching common code
    // as more files move across.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
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
                implementation(project(":pump:insight"))
                implementation(project(":plugins:aps"))
                implementation(project(":plugins:source"))
                implementation(project(":pump:dana"))
                implementation(project(":pump:danar"))
                implementation(project(":pump:danars"))
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

// Several of the test dependencies above are flavoured Android libraries, and a multiplatform module
// has no flavours of its own, so every classpath that reaches them has to pick one.
listOf(
    "androidCompileClasspath",
    "androidRuntimeClasspath",
    "androidHostTestCompileClasspath",
    "androidHostTestRuntimeClasspath"
).forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(com.android.build.api.attributes.ProductFlavorAttr.of("standard"), objects.named("full"))
        }
    }
}

tasks.withType<Test> {
    // useJUnitPlatform() and the heap cap come from kmp-test-defaults; only the JaCoCo part is
    // specific here. Restated from jacoco-module-dependencies, which applies com.android.library.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
