plugins {
    kotlin("multiplatform")
}

// A framework that links every module already migrated to Kotlin Multiplatform.
//
// This module ships no features. It exists to answer one question that compiling klibs cannot:
// does the migrated code actually LINK into an iOS binary? Compilation only checks each module on
// its own, while linking resolves the whole graph at once, so it is the step that finds a missing
// `actual`, a dependency with no Apple artifact, or a symbol nothing provides.
//
// Every module is `export`ed rather than only depended on. An exported dependency has its API
// linked in and written into the framework header, so the link covers the real public surface
// instead of the small part a hand written entry point would happen to touch.
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

            export(project(":core:data"))
            export(project(":core:graph"))
            export(project(":core:interfaces"))
            export(project(":core:keys"))
            export(project(":core:nssdk"))
            export(project(":core:objects"))
            export(project(":core:ui"))
            export(project(":core:utils"))
            export(project(":plugins:aps"))
            export(project(":plugins:calibration"))
            export(project(":plugins:main"))
            export(project(":plugins:sensitivity"))
            export(project(":plugins:smoothing"))
            export(project(":pump:virtual"))
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // `api`, not `implementation`: a module can only be exported to the framework when
                // it is part of this module's own API.
                api(project(":core:data"))
                api(project(":core:graph"))
                api(project(":core:interfaces"))
                api(project(":core:keys"))
                api(project(":core:nssdk"))
                api(project(":core:objects"))
                api(project(":core:ui"))
                api(project(":core:utils"))
                api(project(":plugins:aps"))
                api(project(":plugins:calibration"))
                api(project(":plugins:main"))
                api(project(":plugins:sensitivity"))
                api(project(":plugins:smoothing"))
                api(project(":pump:virtual"))
            }
        }
    }
}
