plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as :core:graph, :core:ui, :core:objects and the rest.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

// Generates UiStrings (commonMain) and UiStringIds (androidMain) from this module's strings.xml, the
// same generator :core:ui, :core:keys and :implementation use. It lets a screen name its user text
// instead of numbering it, which is what a commonMain composable needs. The strings themselves do not
// move, and AAPT keeps resolving them on Android exactly as before.
val generateUiStrings = tasks.register<GenerateKeyStringsTask>("generateUiStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.ui")
    owner.set("ui")
    objectName.set("UiStrings")
    idsObjectName.set("UiStringIds")
    reportFile.set(layout.buildDirectory.file("reports/uiStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only derives a convention from the task name, so
    // both properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/uiStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/uiStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.ui"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
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
        // The modules and the Compose artifacts a shared screen needs. Compose Multiplatform
        // republishes the same `androidx.compose.*` package names, so a screen that only uses Compose
        // moves here unchanged - that is how :core:ui ended up with 435 of its files in commonMain.
        commonMain {
            kotlin.srcDir(generateUiStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:graph"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))

                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                api(libs.cmp.material3)
                api(libs.cmp.material.icons.extended)
                // The JetBrains republish, not androidx.lifecycle: same `androidx.lifecycle.*` package names,
                // but with Apple targets. Same choice as :core:ui and :plugins:calibration.
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
                api(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                // Ktor rather than OkHttp so the wiki search runs on every target. Same split as
                // :core:nssdk: the engine is per platform, the code is not.
                implementation(libs.io.ktor.client.core)
                implementation(libs.cmp.ui.tooling.preview)
                // A Compose Multiplatform library - it publishes iosArm64, jvm and wasm too, so the
                // reorderable list works everywhere and does not pin a screen to Android.
                // Same choice as :plugins:automation.
                implementation(libs.sh.calvin.reorderable)
            }
        }

        // Still Android: the widgets are RemoteViews, and Glance, WorkManager, OkHttp and the
        // activity/lifecycle integrations have no iOS side. Screens move to commonMain from here.
        iosMain {
            dependencies {
                implementation(libs.io.ktor.client.darwin)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateUiStrings.flatMap { it.androidOutputDir })
            dependencies {
                api(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.activity.compose)
                api(libs.androidx.compose.runtime)
                api(libs.androidx.lifecycle.runtime.compose)
                api(libs.androidx.ui.tooling.preview)
                // Was debugImplementation; the multiplatform library target has no build types.
                implementation(libs.androidx.ui.tooling)
                implementation(libs.androidx.glance.appwidget)
                implementation(libs.androidx.work.runtime)
                implementation(libs.androidx.core)

                // Ktor engine for this target. Replaces the direct OkHttp dependency the wiki search
                // used before it was ported.
                implementation(libs.io.ktor.client.okhttp)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                // The real org.json, not the android.jar stub: with isReturnDefaultValues the stub
                // answers null and the failure surfaces far away, as an NPE inside a test fixture.
                implementation(libs.org.json.android)
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

