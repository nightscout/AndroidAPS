plugins {
    kotlin("multiplatform")
    // NOT com.android.library - AGP 9 refuses that together with the multiplatform plugin.
    alias(libs.plugins.android.kmp.library)
    // The Compose COMPILER, which ships with Kotlin and compiles @Composable for every target
    // including Kotlin/Native. org.jetbrains.compose below hard-fails without it.
    alias(libs.plugins.compose.compiler)
    // The Compose Multiplatform framework itself.
    alias(libs.plugins.compose.multiplatform)
}

// THROWAWAY SPIKE. Not part of the app, consumed by nothing, safe to delete.
//
// It answers one question: does Compose Multiplatform work in THIS build - this Kotlin, this AGP,
// this catalog, next to the androidx Compose the app already uses. The general form of that question
// is already answered in public (coil-kt/coil and plainhub/plain-app both ship the same four plugins
// on Kotlin 2.4.10 + AGP 9.3.1 + CMP 1.11.1), so this only checks that nothing repo-specific
// interferes.
//
// Deliberately NOT here:
//  - compose.components.resources. compose-resources was rejected for AAPS - it cannot match a
//    region-less locale against a region-pinned folder. generateResClass defaults to `auto`, which
//    generates nothing unless that dependency is present, so leaving it out is the whole opt-out.
//  - a jvm() target. It would pull in the desktop Compose surface (skiko-awt) and give the spike
//    another way to fail without saying anything about iOS.
//  - androidResources. Off by default for a KMP library, and this module owns no res/, which is also
//    why CMP-9547 (resources not packaged under AGP 9) cannot apply here.
kotlin {
    android {
        namespace = "app.aaps.spike.cmp"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
        // Restated because android-module-dependencies applies com.android.library and so cannot be
        // applied to a multiplatform module - same reason as core/keys.
        lint { checkReleaseBuilds = false }
    }

    // Apple klibs cross compile on Windows. Linking and running still need a Mac and report SKIPPED.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
            // Needed by the real AAPS files copied in below: both use Icons.Filled.Remove, which is
            // in the extended set rather than the core one.
            implementation(libs.cmp.material.icons.extended)

            // The point of the spike is UI on top of the REAL shared spine, not against stubs.
            // Both of these already build for iosArm64 / iosSimulatorArm64.
            implementation(project(":core:data"))
            implementation(project(":core:keys"))
        }
    }
}
