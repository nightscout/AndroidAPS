import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
}

repositories {
    mavenCentral()
    google()
}

fun generateGitBuild(): String {
    try {
        val processBuilder = ProcessBuilder("git", "describe", "--always", "--abbrev=7")
        val output = File.createTempFile("git-build", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateGitRemote(): String {
    try {
        val processBuilder = ProcessBuilder("git", "remote", "get-url", "origin")
        val output = File.createTempFile("git-remote", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
    return stringBuilder.toString()
}

fun isMaster(): Boolean = !Versions.appVersion.contains("-")

fun gitAvailable(): Boolean {
    try {
        val processBuilder = ProcessBuilder("git", "--version")
        val output = File.createTempFile("git-version", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().isNotEmpty()
    } catch (_: Exception) {
        return false
    }
}

fun allCommitted(): Boolean {
    try {
        val processBuilder = ProcessBuilder("git", "status", "-s")
        val output = File.createTempFile("git-comited", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().replace(Regex("""(?m)^\s*(M|A|D|\?\?)\s*.*?\.idea\/codeStyles\/.*?\s*$"""), "")
            // ignore all files added to project dir but not staged/known to GIT
            .replace(Regex("""(?m)^\s*(\?\?)\s*.*?\s*$"""), "").trim().isEmpty()
    } catch (_: Exception) {
        return false
    }
}

android {

    namespace = "app.aaps"

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk

        buildConfigField("String", "VERSION", "\"$version\"")
        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
        buildConfigField("String", "REMOTE", "\"${generateGitRemote()}\"")
        buildConfigField("String", "HEAD", "\"${generateGitBuild()}\"")
        buildConfigField("String", "COMMITTED", "\"${allCommitted()}\"")

        // For Hilt injected instrumentation tests in app module
        testInstrumentationRunner = "app.aaps.runners.HiltTestRunner"
    }

    flavorDimensions += "standard"
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "app_name", "AAPS")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_launcher_round"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "app_name", "Pumpcontrol")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
            manifestPlaceholders["appIconRound"] = "@null"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_blueowl"
        }
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient3")
            versionName = Versions.appVersion + "-aapsclient3"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_greenowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_greenowl"
        }
    }

    useLibrary("org.apache.http.legacy")

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    // ---- Gradle Managed Devices (DRAFT — not yet wired into .circleci/config.yml) -----------------
    // Splits the app module's androidTest suite across emulators WITHOUT hand-rolling coverage or
    // result collection: AGP owns the emulator lifecycle and merges each shard's JaCoCo .ec files and
    // JUnit XMLs through the normal pipeline, so jacocoAllDebugReport / Codecov keep working. The
    // system image mirrors the hand-launched CI emulator (android-31, google_apis_playstore, x86_64);
    // adopting this replaces the `emulator -avd citest` + taskset launch in the CI config, so it is a
    // real change to that file — kept here as a reviewable draft.
    //
    // Two ways to drive it (choose in the CI config):
    //   1. AUTO-shard by test COUNT across N instances of `emu` — annotations unused, a new test
    //      distributes itself, zero maintenance, but balance is approximate (count, not time):
    //        ./gradlew :app:emuFullDebugAndroidTest \
    //          -Pandroid.experimental.androidTest.numManagedDeviceShards=2
    //   2. EXPLICIT time-balance via the @ShardA annotation (the ~278s/277s split we measured) —
    //      run each shard as its own task, filtered, on its own device:
    //        :app:emuAFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=app.aaps.testcategories.ShardA
    //        :app:emuBFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=app.aaps.testcategories.ShardA
    //      Caveat: two Gradle invocations of the same module collide on build outputs, so option 2
    //      needs them serialised or in separate checkouts. Option 1 is the simpler parallel path;
    //      option 2 buys guaranteed balance for this lopsided suite at the cost of that orchestration.
    testOptions {
        managedDevices {
            localDevices {
                create("emu") {   // for option 1 (auto-shard: numManagedDeviceShards=2 spins up 2 instances)
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "google_apis_playstore"
                }
                create("emuA") {  // for option 2 (explicit @ShardA balance across two devices)
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "google_apis_playstore"
                }
                create("emuB") {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "google_apis_playstore"
                }
            }
        }
    }

    sourceSets {
        getByName("full") { kotlin.directories.add("src/withPumps/kotlin") }
        getByName("pumpcontrol") { kotlin.directories.add("src/withPumps/kotlin") }
        getByName("aapsclient2") { kotlin.directories.add("src/aapsclient/kotlin") }
        getByName("aapsclient3") { kotlin.directories.add("src/aapsclient/kotlin") }
    }
}

allprojects {
    repositories {
    }
}

dependencies {
    implementation(project(":shared:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":ui"))
    // Feature plugins self-register into the Hilt plugin map (see e.g. :plugins:smoothing SmoothingModule).
    // Adding/removing a plugin is therefore just an include in settings.gradle — no edit needed here.
    rootProject.subprojects
        .filter { it.path.startsWith(":plugins:") && it.buildFile.exists() }
        .forEach { implementation(project(it.path)) }
    implementation(project(":implementation"))
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))

    // Pump drivers — only for full + pumpcontrol flavors. Derived from the :pump:* modules included
    // in settings.gradle (single source of truth) minus two exceptions:
    //  - :pump:virtual is @AllConfigs (all flavors) and is wired above as a plain implementation
    //  - :pump:combov2:comboctl is a support lib pulled in transitively by :pump:combov2
    // buildFile.exists() skips the phantom :pump:omnipod container Gradle auto-creates from the
    // nested :pump:omnipod:* includes (it has no build script / no consumable variant).
    val pumpExclusions = setOf(":pump:virtual", ":pump:combov2:comboctl")
    rootProject.subprojects
        .filter { it.path.startsWith(":pump:") && it.path !in pumpExclusions && it.buildFile.exists() }
        .forEach {
            "fullImplementation"(project(it.path))
            "pumpcontrolImplementation"(project(it.path))
        }

    implementation(libs.androidx.lifecycle.process)

    testImplementation(project(":shared:tests"))
    androidTestImplementation(project(":shared:tests"))
    androidTestImplementation(libs.androidx.test.rules)
    // UiAutomator for the in-process E2E UI test (app/src/androidTest/.../e2e). Drives the real
    // Compose UI (booted under the Hilt test app) via the accessibility bridge.
    androidTestImplementation(libs.androidx.test.uiautomator)
    // Initializes WorkManager for instrumented tests (BaseTestApp), since the production
    // Configuration.Provider/manifest initializer don't apply under the Hilt test application.
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.org.skyscreamer.jsonassert)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Rhino is needed by the openAPS adapter test fixtures under app/src/androidTest
    // (these files reference org.mozilla.javascript.* classes directly).
    androidTestImplementation(libs.org.mozilla.rhino)

    debugImplementation(libs.com.squareup.leakcanary.android)

    /* Dagger2 - We are going to use dagger.android which includes
     * support for Activity and fragment injection so we need to include
     * the following dependencies */
    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)
    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)
    // Hilt WorkManager integration: HiltWorkerFactory + @HiltWorker assisted-injection glue.
    // androidx.hilt:hilt-compiler is a SEPARATE annotation processor from the dagger hilt-compiler above.
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    // Hilt instrumentation testing: lets androidTest reuse the production @InstallIn graph
    // (single source of truth) with @TestInstallIn overrides instead of a hand-maintained component.
    androidTestImplementation(libs.com.google.dagger.hilt.android.testing)
    // KSP no longer inherits main-config processors into androidTest (KSP 2.3.10+), so the Hilt
    // test-app (@CustomTestApplication → HiltTestApplication_Application) and @HiltAndroidTest
    // graph must be generated by registering the processors on the androidTest config explicitly.
    kspAndroidTest(libs.com.google.dagger.hilt.compiler)
    kspAndroidTest(libs.com.google.dagger.compiler)
    kspAndroidTest(libs.com.google.dagger.android.processor)

    // MainApp
    implementation(libs.com.uber.rxdogtag2.rxdogtag)
    // Remote config
    api(libs.com.google.firebase.config)
    // Navigation Compose
    api(libs.androidx.compose.navigation)
}

println("-------------------")
println("isMaster: ${isMaster()}")
println("gitAvailable: ${gitAvailable()}")
println("allCommitted: ${allCommitted()}")
println("-------------------")
if (!gitAvailable()) {
    throw GradleException("GIT system is not available. On Windows try to run Android Studio as an Administrator. Check if GIT is installed and Studio have permissions to use it")
}
if (isMaster() && !allCommitted()) {
    throw GradleException("There are uncommitted changes. Clone sources again as described in wiki and do not allow gradle update")
}

