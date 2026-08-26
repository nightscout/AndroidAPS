plugins {
    kotlin("multiplatform")
}

// A framework that links every module already migrated to Kotlin Multiplatform.
//
// This module ships no features. It exists to answer one question that compiling klibs cannot:
// does the migrated code actually LINK into an iOS binary? Compilation only checks each module on
// its own, while linking resolves the whole graph at once, so it is the step that finds a missing
// `actual`, a dependency with no Apple artifact, or a symbol nothing provides.
val migratedModules = listOf(
    ":core:data",
    ":core:graph",
    ":core:interfaces",
    ":core:keys",
    ":core:nssdk",
    ":core:objects",
    ":core:ui",
    ":core:utils",
    ":plugins:aps",
    ":plugins:calibration",
    ":plugins:main",
    ":plugins:sensitivity",
    ":plugins:smoothing",
    ":pump:virtual"
)

// Whether the migrated API is written into the framework header for Swift to call.
//
// On. Exporting generates an Objective-C header for every public declaration, which is a stricter
// check than linking: a name that is legal in Kotlin can still be illegal in Objective-C. It caught
// `QuickWizardEntry.YES` and `NO`, since both are macros there, and the header came out as
// `int32_t __objc_no`. Those are now ALWAYS and NEVER.
//
// Swift callers see every exported name at once, so a Kotlin class can shadow a system type. AAPS
// has a `Scene`, which hides SwiftUI's, and the app writes `SwiftUI.Scene` to say which it means. A
// real app would export a smaller, chosen surface instead of all fourteen modules.
val exportMigratedApi = true

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "AapsShared"
            // Static: nothing here is loaded at runtime by another framework, and a static link
            // reports an unresolved symbol as a build error instead of deferring it to app start.
            isStatic = true

            if (exportMigratedApi) {
                migratedModules.forEach { export(project.dependencies.project(it)) }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // `api`, not `implementation`: a module can only be exported to the framework when
                // it is part of this module's own API, and `exportMigratedApi` turns that on.
                migratedModules.forEach { api(project.dependencies.project(it)) }
            }
        }
    }
}
