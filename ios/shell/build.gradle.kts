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
// Off, because it does not compile yet. Exporting generates an Objective-C header for every public
// declaration, and `QuickWizardEntry.Companion` has `const val YES` and `const val NO`. In
// Objective-C those two names are macros, so the header comes out as `int32_t __objc_no` and clang
// rejects it. That is a real thing to fix before Swift can use these modules, but it is a separate
// problem from whether the code links, and renaming those constants would change shared code.
//
// With this off the modules are still `api` dependencies, so all of them are still linked into the
// binary. Only the generated header shrinks to this module's own API.
val exportMigratedApi = false

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
