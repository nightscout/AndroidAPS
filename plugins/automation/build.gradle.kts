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

// Generates AutomationStrings (commonMain) and AutomationStringIds (androidMain) from this module's
// strings.xml, the same generator :ui, :core:ui, :core:keys and :implementation use. It lets a trigger
// or action name its user text instead of numbering it, which is what commonMain code needs. The
// strings themselves do not move, and AAPT keeps resolving them on Android exactly as before.
val generateAutomationStrings = tasks.register<GenerateKeyStringsTask>("generateAutomationStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.automation")
    owner.set("automation")
    objectName.set("AutomationStrings")
    idsObjectName.set("AutomationStringIds")
    reportFile.set(layout.buildDirectory.file("reports/automationStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only derives a convention from the task name, so
    // both properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/automationStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/automationStrings/android"))
}


kotlin {
    android {
        namespace = "app.aaps.plugins.automation"
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

    // Keeping the targets is what stops an android-only import from quietly reaching common code as
    // files move across. commonMain holds the platform ports so far, such as PairedBtDevices.
    //
    // Rule for that move: the action or trigger class itself is shared, and only the call it cannot
    // make everywhere is lifted out behind an interface implemented per platform. ActionSendSMS needs
    // nothing here - it already goes through the SmsCommunicator interface.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // The triggers, the actions and their screens live here. androidMain inherits all of this,
        // so nothing below is repeated there.
        commonMain {
            kotlin.srcDir(generateAutomationStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:utils"))
                implementation(project(":core:ui"))

                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                api(libs.cmp.material3)
                api(libs.cmp.material.icons.extended)
                // The JetBrains republish, not androidx.lifecycle: same `androidx.lifecycle.*` package
                // names, but with Apple targets. Same choice as :core:ui and :ui.
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                // A Compose Multiplatform library - it publishes iosArm64, jvm and wasm too, so the
                // reorderable list works everywhere and does not pin a screen to Android.
                implementation(libs.sh.calvin.reorderable)
                // The JetBrains republish of the Preview annotation - same package name, with iOS.
                implementation(libs.cmp.ui.tooling.preview)
            }
        }

        iosTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateAutomationStrings.flatMap { it.androidOutputDir })
            dependencies {
                api(libs.com.google.android.gms.playservices.location)
                implementation(libs.kotlin.reflect)
                // OpenStreetMap for map picker
                implementation(libs.org.osmdroid)

                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.ui)
                api(libs.androidx.ui.graphics)
                api(libs.androidx.ui.tooling)
                api(libs.androidx.ui.tooling.preview)
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)
                api(libs.androidx.lifecycle.runtime.compose)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both apply com.android.library and so cannot be
        // used by a multiplatform module. Same approach as :plugins:aps.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":shared:impl"))
                implementation(project(":implementation"))
                implementation(project(":plugins:main"))
                implementation(project(":pump:virtual"))

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
                implementation(libs.org.skyscreamer.jsonassert)
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
