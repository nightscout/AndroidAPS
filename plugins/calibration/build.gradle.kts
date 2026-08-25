plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro is a Kotlin COMPILER plugin, not KSP. It is pinned to the Kotlin version, so the first
    // question is simply whether a build exists for the Kotlin this repo uses. Same one line as
    // :plugins:smoothing - there is no processor to configure and no generated source directory.
    alias(libs.plugins.metro)
}

// Same generator as :core:ui, :pump:virtual and :plugins:smoothing, pointed at this module's strings.
val generateCalibrationStrings = tasks.register<GenerateKeyStringsTask>("generateCalibrationStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.calibration")
    owner.set("calibration")
    objectName.set("CalibrationStrings")
    idsObjectName.set("CalibrationStringIds")
    reportFile.set(layout.buildDirectory.file("reports/calibrationStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory derives its convention from the task name, so both
    // properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/calibrationStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/calibrationStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.plugins.calibration"
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
        commonMain {
            kotlin.srcDir(generateCalibrationStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:ui"))

                implementation(libs.cmp.runtime)
                implementation(libs.cmp.foundation)
                implementation(libs.cmp.ui)
                implementation(libs.cmp.material3)
                implementation(libs.cmp.material.icons.extended)
                implementation(libs.cmp.ui.tooling.preview)
                // The multiplatform lifecycle: ViewModel, viewModelScope, viewModel { } and
                // collectAsStateWithLifecycle all come from here rather than from androidx, which
                // would pin the screen and its view model to Android.
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateCalibrationStrings.flatMap { it.androidOutputDir })
            dependencies {
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.ui)
                implementation(libs.androidx.compose.ui.tooling)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":implementation"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                implementation(libs.org.robolectric)
                // The real org.json - see :pump:virtual for why isReturnDefaultValues needs it.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

// :shared:tests and :implementation are flavoured Android libraries, so the host test classpath has
// to pick a flavour or resolution is ambiguous.
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
