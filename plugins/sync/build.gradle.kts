plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

// Generates SyncStrings (commonMain) and SyncStringIds (androidMain) from this module's res/values,
// the same generator the other plugins use. The strings themselves do not move, and AAPT keeps
// resolving them on Android exactly as before.
val generateSyncStrings = tasks.register<GenerateKeyStringsTask>("generateSyncStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.sync")
    owner.set("sync")
    objectName.set("SyncStrings")
    idsObjectName.set("SyncStringIds")
    reportFile.set(layout.buildDirectory.file("reports/syncStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only derives a convention from the task name, so
    // both properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/syncStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/syncStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.plugins.sync"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        // The instrumented Garmin tests, in src/androidDeviceTest. Without the runner named here the
        // device test builds but has nothing to run it.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Declared so an Android import cannot quietly reach shared code. Most of this module stays on
    // Android for now - okhttp, retrofit, appauth, Play Services and Garmin have no Apple artifacts.
    // The client-control screens are what needs to move: :appshell routes to them.
    iosArm64()
    iosSimulatorArm64()

    // Desktop (Windows/macOS/Linux). Compose Multiplatform resolves its `desktop` variant from a
    // plain jvm() target, so no special target name is needed.
    jvm()

    // Android and desktop share the socket.io client. Applied explicitly, because the manual
    // dependsOn below would otherwise switch the automatic hierarchy off and silently unwire iosMain.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        // socket.io is a Java library, so the Nightscout websocket client is the same code on a
        // phone and on a desktop. Only the JSON implementation under it differs - see jvmMain.
        jvmSharedMain.dependencies {
            implementation(libs.io.socket.client)
        }

        // Tests of shared code belong here, not in a per-target set: NsWsPayloadTest lived in
        // iosTest, which only runs on a simulator, so a bug in commonMain code went unnoticed on
        // every other machine until a desktop run hit it.
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        commonMain {
            kotlin.srcDir(generateSyncStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))
                implementation(project(":core:nssdk"))

                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                api(libs.cmp.material3)
                api(libs.cmp.material.icons.extended)
                // NavigationBackHandler: the multiplatform replacement for androidx.activity BackHandler.
                implementation(libs.androidx.navigationevent.compose)
                api(libs.jetbrains.lifecycle.viewmodel.compose)
                api(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.cmp.ui.tooling.preview)
                implementation(libs.kotlinx.datetime)
            }
        }

        // Tests for the iOS websocket implementation. They run on the simulator, which is the only
        // place the Kotlin/Native behaviour is actually exercised.
        iosTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateSyncStrings.flatMap { it.androidOutputDir })
            dependencies {
                implementation(project(":shared:impl"))

                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)
                api(libs.androidx.lifecycle.runtime.compose)
                api(libs.androidx.ui.tooling.preview)
                implementation(libs.androidx.compose.ui.tooling)

                // OpenHumans
                api(libs.com.squareup.okhttp3.okhttp)
                api(libs.com.squareup.retrofit2.retrofit)
                implementation(libs.androidx.browser)

                // NSClient, Tidepool
                api(libs.io.socket.client)
                implementation(libs.com.squareup.okhttp3.logging.interceptor)
                implementation(libs.com.squareup.retrofit2.converter.gson)
                api(libs.com.google.code.gson)
                api(libs.net.openid.appauth)

                // DataLayerListenerService
                api(libs.com.google.android.gms.playservices.wearable)

                // SMS Communicator (OTP + QR code)
                implementation(libs.com.eatthepath.java.otp)
                implementation(libs.com.github.kenglxn.qrgen.android)
                // ZXing is pulled transitively by qrgen but SmsCommunicatorOtpScreen imports
                // ErrorCorrectionLevel directly - declare it so a qrgen upgrade cannot drop it.
                implementation(libs.com.google.zxing.core)

                // Garmin
            }
        }

        // Hand written rather than taken from the test convention plugins, because those apply
        // com.android.library and so cannot be used by a multiplatform module.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":implementation"))
                implementation(project(":plugins:aps"))

                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.work.testing)
                implementation(libs.joda.time)
                implementation(libs.org.skyscreamer.jsonassert)
                implementation(libs.org.robolectric)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.androidx.compose.ui.test.manifest)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null
                // rather than throwing, which NPEs the shared profile fixtures.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }

        // Instrumented tests run on JUnit 4, not the JUnit 5 the host tests use. These were supplied
        // by test-module-dependencies before, which a multiplatform module cannot apply.
        getByName("androidDeviceTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(libs.androidx.test.ext)
                implementation(libs.androidx.test.rules)
                implementation(libs.com.google.truth)
                implementation(libs.org.mockito.android)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.kotlinx.coroutines.test)
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

// The Garmin SDK ships as an aar and needs the artifact type spelled out. The source-set dependency
// DSL has no overload taking a configuration closure, so it is declared by configuration name here.
dependencies {
    "androidMainApi"(libs.com.garmin.connectiq) { artifact { type = "aar" } }
    "androidDeviceTestImplementation"(libs.com.garmin.connectiq) { artifact { type = "aar" } }
}

// :shared:tests carries JUnit 5 for the host tests, and it reaches the device test through
// TestBase. Dexing those jars fails - JUnit 6 uses Java records, which D8 cannot desugar in this
// configuration - and nothing on the device needs them, because the instrumented tests are JUnit 4.
configurations.named("androidDeviceTestImplementation") {
    exclude(group = "org.junit.jupiter")
    exclude(group = "org.junit.platform")
}
