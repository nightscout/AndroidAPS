import kotlin.math.min

plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as :core:keys, :core:data, :core:utils and :core:interfaces.
    alias(libs.plugins.android.kmp.library)
    // The Compose COMPILER, which ships with Kotlin and compiles @Composable for every target.
    alias(libs.plugins.compose.compiler)
    // The Compose Multiplatform framework. The compiler plugin above is applied per project rather
    // than per target, so every target needs a Compose runtime on its class path.
    alias(libs.plugins.compose.multiplatform)
}

// One task, not one per variant: a multiplatform module has no product flavours, and a Kotlin source
// set takes a task provider directly. Same generator as :core:keys and :core:interfaces, pointed at
// this module's strings. The strings themselves do not move, and AAPT keeps resolving them on
// Android exactly as before.
val generateUiStrings = tasks.register<GenerateKeyStringsTask>("generateUiStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.core.ui")
    owner.set("ui")
    objectName.set("UiStrings")
    idsObjectName.set("UiStringIds")
    reportFile.set(layout.buildDirectory.file("reports/uiStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only applies a convention derived from the task
    // name, so both properties would land on one directory and the second file written would delete
    // the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/uiStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/uiStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.core.ui"
        compileSdk = Versions.compileSdk
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
        // Off by default for a multiplatform library, unlike a plain android library. This module is
        // where most of the app's strings, drawables and raw alarm sounds live, so it must be on.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        // isIncludeAndroidResources is what makes Robolectric work: the Compose UI tests here need a
        // real merged resource table and the manifest that holds the activity createComposeRule()
        // launches. Restated from test-module-dependencies, which this module can no longer apply.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply. Without
        // it MissingTranslation would switch on for the first time here and every locale file that
        // is incomplete today would fail a release build.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows. Linking and running still need a Mac, and those tasks
    // report SKIPPED rather than failing. Keeping the target is what stops an android-only import
    // from quietly reaching commonMain later.
    //
    // Deliberately no jvm() target, as in :core:interfaces: it pulls in the desktop Compose surface
    // (skiko-awt) and gives the module another way to fail without saying anything about iOS.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateUiStrings.flatMap { it.commonOutputDir })
            dependencies {
                api(project(":core:data"))
                api(project(":core:interfaces"))
                api(project(":core:keys"))
                api(libs.kotlinx.datetime)

                // CMP rather than androidx. On Android CMP delegates to androidx, so the composeBom
                // below still decides the Android versions and nothing about the Android build
                // changes.
                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                // Replaces the deprecated androidx.compose.ui.backhandler.BackHandler.
                api(libs.androidx.navigationevent.compose)
                api(libs.cmp.material3)
                api(libs.cmp.material.icons.extended)
                // viewModel() for Compose. The JetBrains republish, not androidx: it is the one with Apple
                // targets, and `metroViewModel()` below has to compile wherever the shared UI does.
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                // Metro's view model extension. `api` so every module with a view model gets @ViewModelKey
                // without repeating the dependency - there are eighty of them to convert.
                api(libs.metrox.viewmodel)
                implementation(libs.cmp.ui.tooling.preview)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateUiStrings.flatMap { it.androidOutputDir })
            dependencies {
                // Everything here was `api` on the old android library and the consumer modules
                // resolve it transitively, so it must stay exported.
                api(libs.androidx.appcompat)
                api(libs.com.google.android.material)
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)
                api(libs.androidx.compose.runtime)
                api(libs.androidx.activity.compose)
                api(libs.androidx.lifecycle.runtime.compose)

                api(libs.com.google.dagger.android)
                api(libs.com.google.dagger.android.support)

                implementation(libs.androidx.compose.ui.tooling.preview)
                // Was debugImplementation. The AGP multiplatform library target has no build types,
                // so there is no debug-only configuration to put it in. It only matters for rendering
                // @Preview, and R8 in the app module drops it from a release build.
                implementation(libs.androidx.compose.ui.tooling)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both convention plugins apply
        // com.android.library and so cannot be used here.
        //
        // createComposeRule() is a JUnit4 TestRule and RobolectricTestRunner is a JUnit4 runner, so
        // these tests run JUnit4 style; the vintage engine bridges them onto the JUnit Platform.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                // Was debugImplementation: supplies the manifest holding the activity that
                // createComposeRule() launches.
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<Test> {
    // useJUnitPlatform() and the heap cap come from kmp-test-defaults; only the JaCoCo part is
    // specific to this module.
    // Robolectric runs tests in its own classloader sandbox and rewrites bytecode, so the default
    // JaCoCo on-the-fly agent records no coverage for the classes those tests exercise - here that is
    // every Compose screen the UI tests drive. Restated from jacoco-module-dependencies, which applies
    // com.android.library and so cannot be used by a multiplatform module. The jacoco plugin itself is
    // already applied to every project by the root build file.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
