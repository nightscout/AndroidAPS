plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules and the other converted plugins.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

// No `metro { interop { includeDagger() } }` here, unlike :plugins:aps. Nothing in this module carries
// a javax annotation any more - the Dagger processors and the last 18 `javax.inject.Inject` went in the
// same change that made it multiplatform.

kotlin {
    android {
        namespace = "app.aaps.plugins.source"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
        // InstaraStaleCheckWorker's test needs a real Context for WorkManager and Intent broadcasts.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply. Without it
        // MissingTranslation would switch on here and every incomplete locale file would fail a release
        // build.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Declared even though nothing is in commonMain yet. Keeping the targets is what stops an
    // android-only import from quietly reaching common code once files start moving across.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))
                // :ui is still a plain Android library, so it can only be reached from here.
                implementation(project(":ui"))

                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.compose.runtime)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.work.runtime)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both apply com.android.library and so cannot be
        // used by a multiplatform module. Same approach as :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                // InstaraWorkerTest uses kotlin.test assertions; test-module-dependencies used to supply this.
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.work.testing)
                // Robolectric for the InstaraStaleCheckWorker test: it needs a real Android Context
                // (WorkManager.getInstance + Intent broadcasts). The vintage engine bridges its JUnit4
                // runner onto the JUnit Platform alongside the Jupiter tests.
                implementation(libs.org.robolectric)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather than
                // throwing, which NPEs the shared profile fixtures in TestBaseWithProfile.
                implementation(libs.org.json.android)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<Test> {
    // useJUnitPlatform() and the heap cap come from kmp-test-defaults; only the JaCoCo part is
    // specific here. Restated from jacoco-module-dependencies, which applies com.android.library.
    // Robolectric rewrites bytecode in its own classloader, so the default on-the-fly agent records
    // no coverage for the classes those tests drive.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
