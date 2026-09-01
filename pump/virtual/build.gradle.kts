plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro builds this module's classes into the graph.
    alias(libs.plugins.metro)
}

// Same generator as :core:ui and :core:interfaces, pointed at this module's strings. The strings
// themselves do not move, and AAPT keeps resolving them on Android exactly as before - this only adds
// a TextRef-named view of them that common code can reach.
val generateVirtualStrings = tasks.register<GenerateKeyStringsTask>("generateVirtualStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.pump.virtual")
    owner.set("virtual")
    objectName.set("VirtualStrings")
    idsObjectName.set("VirtualStringIds")
    reportFile.set(layout.buildDirectory.file("reports/virtualStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory derives its convention from the task name, so both
    // properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/virtualStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/virtualStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.pump.virtual"
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

    // Desktop (Windows/macOS/Linux). Compose Multiplatform resolves its `desktop` variant from a
    // plain jvm() target, so no special target name is needed.
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVirtualStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))

                implementation(libs.cmp.runtime)
                implementation(libs.cmp.foundation)
                implementation(libs.cmp.ui)
                implementation(libs.cmp.material3)
                implementation(libs.cmp.material.icons.extended)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateVirtualStrings.flatMap { it.androidOutputDir })
            dependencies {
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.ui)
                api(libs.androidx.ui.tooling)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :core:ui.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The real org.json. isReturnDefaultValues makes the platform stub answer null
                // instead of throwing "not mocked", which turns JSONObject.toString() into null and
                // NPEs the shared profile fixtures. This is AOSP's own implementation, repackaged -
                // same reason :core:utils declares it.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

