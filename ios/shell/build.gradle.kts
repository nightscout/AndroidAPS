plugins {
    kotlin("multiplatform")
    // Metro, because this module does not only link the migrated code, it builds a graph from it.
    alias(libs.plugins.metro)
    // Compose, because this module also hosts AAPS's UI in a UIViewController. Declaring the
    // dependency transitively through :core:ui is not enough: the declarations resolve at compile
    // time, then `ComposeUIViewController` is missing at link time, which surfaces only when the
    // app runs, as an IrLinkageError.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
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
    ":database:impl",
    ":database:persistence",
    ":implementation",
    ":plugins:aps",
    ":plugins:automation",
    ":plugins:calibration",
    ":plugins:configuration",
    ":plugins:constraints",
    ":plugins:main",
    ":plugins:sensitivity",
    ":plugins:smoothing",
    ":plugins:source",
    ":plugins:sync",
    ":pump:virtual",
    ":shared:impl",
    ":ui",
    ":workflow"
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

// Fails the build when a module gains iOS targets and is not listed above.
//
// The list is written by hand, which is fine for reviewing but useless for noticing. When
// :implementation became multiplatform, this framework carried on linking fourteen modules and
// reported success, so a passing build quietly meant less than it had the day before. That is the
// failure this catches: not a broken build, a shrinking one.
//
// It reads the sibling build files as text rather than inspecting other Gradle projects, because
// inspecting them would couple this module's configuration to theirs and cost project isolation.
val checkMigratedModules = tasks.register("checkMigratedModules") {
    val listed = migratedModules.toSet()
    val buildFiles = rootProject.projectDir.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.name == "build.gradle.kts" }
        .toList()
    val rootDir = rootProject.projectDir

    doLast {
        val withIosTargets = buildFiles
            // Skip comment lines: a doc line that merely mentions the target used to count as
            // declaring it, which is how a KDoc in :appshell once made this fail.
            .filter { f ->
                f.readLines().any { line ->
                    val t = line.trimStart()
                    !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && t.contains("iosArm64()")
                }
            }
            .map { ":" + it.parentFile.relativeTo(rootDir).invariantSeparatorsPath.replace('/', ':') }
            .filterNot { it == ":ios:shell" }
            .toSet()

        val missing = withIosTargets - listed
        val stale = listed - withIosTargets
        check(missing.isEmpty() && stale.isEmpty()) {
            buildString {
                appendLine("ios/shell no longer covers every module that builds for iOS.")
                if (missing.isNotEmpty()) appendLine("  add to migratedModules:    " + missing.sorted().joinToString())
                if (stale.isNotEmpty()) appendLine("  no longer builds for iOS:  " + stale.sorted().joinToString())
                append("Keep ShellInfo.LINKED_MODULES in step as well.")
            }
        }
    }
}

tasks.matching { it.name.startsWith("linkDebugFramework") }.configureEach { dependsOn(checkMigratedModules) }

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

                // Named here rather than inherited, so the iOS parts of Compose are in the link.
                implementation(libs.cmp.runtime)
                implementation(libs.cmp.foundation)
                implementation(libs.cmp.ui)
                implementation(libs.cmp.material3)
            }
        }

        // Runs on the simulator through :ios:shell:iosSimulatorArm64Test. These cover the same
        // ground the on screen checks do, but a machine reads the result instead of a person.
        iosTest {
            dependencies {
                implementation(kotlin("test"))
                // runTest, because everything the repository exposes is a suspend function.
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
