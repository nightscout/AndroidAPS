plugins {
    // AGP 9 has built-in Kotlin support, so no separate kotlin-android plugin is applied (matches :benchmark).
    id("com.android.test")
}

/**
 * Standalone end-to-end UI test module. It is a `com.android.test` module (like `:benchmark`) that
 * instruments `:app` from its OWN process with a plain `AndroidJUnitRunner`. Crucially that means the
 * app under test runs as the **real production `MainApp`** (no Hilt test-app swap, no system-property
 * hack), and — being a separate process — the test can `pm clear` the app in `@Before` for guaranteed
 * fresh wizard state. The single E2E test drives the full setup wizard with UiAutomator.
 */
android {
    namespace = "app.aaps.e2e"
    compileSdk = Versions.compileSdk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += listOf("standard")
    productFlavors {
        create("full") { dimension = "standard" }
        create("pumpcontrol") { dimension = "standard" }
        create("aapsclient") { dimension = "standard" }
        create("aapsclient2") { dimension = "standard" }
        create("aapsclient3") { dimension = "standard" }
    }

    compileOptions {
        sourceCompatibility = Versions.javaVersion
        targetCompatibility = Versions.javaVersion
    }

    // Instrument :app; run the test in its own process so the app runs as the real production app.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    beforeVariants(selector().all()) {
        // Only the fullDebug variant is meaningful: it tests :app:fullDebug. Disable the rest so CI
        // doesn't build needless variants.
        it.enable = it.buildType == "debug" && it.productFlavors.any { (_, flavor) -> flavor == "full" }
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.uiautomator)
}

// `org.gradle.parallel=true` could otherwise let this connected test run at the same time as :app's
// instrumented tests on the one CI emulator. This test `pm clear`s the app, which would corrupt those,
// so order it strictly after them (a no-op when :app's task isn't in the graph, e.g. running :e2e alone).
tasks.matching { it.name == "connectedFullDebugAndroidTest" }.configureEach {
    mustRunAfter(":app:connectedFullDebugAndroidTest")
}
