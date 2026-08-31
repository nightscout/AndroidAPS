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


// Generates ConfigurationStrings (commonMain) and ConfigurationStringIds (androidMain) from this
// module's strings.xml, the same generator :ui, :plugins:automation and the :core modules use. The
// strings themselves do not move, and AAPT keeps resolving them on Android exactly as before.
val generateConfigurationStrings = tasks.register<GenerateKeyStringsTask>("generateConfigurationStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.configuration")
    owner.set("configuration")
    objectName.set("ConfigurationStrings")
    idsObjectName.set("ConfigurationStringIds")
    reportFile.set(layout.buildDirectory.file("reports/configurationStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only derives a convention from the task name, so
    // both properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/configurationStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/configurationStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.plugins.configuration"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
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
        // The setup wizard and its elements live here. androidMain inherits all of this.
        commonMain {
            kotlin.srcDir(generateConfigurationStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:nssdk"))
                implementation(project(":core:ui"))

                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                api(libs.cmp.material3)
                api(libs.cmp.material.icons.extended)
                // The JetBrains republish, not androidx.lifecycle: same package names, with Apple targets.
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.cmp.ui.tooling.preview)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateConfigurationStrings.flatMap { it.androidOutputDir })
            dependencies {
                // `api` as before: consumers resolve these transitively.
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.runtime)
                api(libs.androidx.lifecycle.runtime.compose)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both apply com.android.library and so cannot be
        // used by a multiplatform module. Same approach as :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":implementation"))
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


tasks.withType<Test> {
    // useJUnitPlatform() and the heap cap come from kmp-test-defaults; only the JaCoCo part is
    // specific here. Restated from jacoco-module-dependencies, which applies com.android.library.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
