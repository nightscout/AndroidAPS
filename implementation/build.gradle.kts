plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as :core:ui, :core:interfaces and the other multiplatform modules.
    alias(libs.plugins.android.kmp.library)
    // Metro is the only DI framework in this module now. Dagger's processor is gone - the last
    // `@InstallIn` module moved to :app - which is what made the multiplatform flip possible at all.
    alias(libs.plugins.metro)
    // The Compose COMPILER, which ships with Kotlin and compiles @Composable for every target.
    alias(libs.plugins.compose.compiler)
    // The Compose Multiplatform framework. The compiler plugin above is applied per project rather
    // than per target, so every target needs a Compose runtime on its class path.
    alias(libs.plugins.compose.multiplatform)
    // Opens @OpenForTesting classes for Mockito. Applied directly rather than through
    // all-open-dependencies, which applies com.android.library and so cannot be used here.
    kotlin("plugin.allopen")
}

metro {
    interop {
        // Lets Metro read the javax annotations still on this module's classes. They are JVM only,
        // so a class keeping them also stays in androidMain - that is the order of work, not a
        // blocker.
        includeDagger()
    }
}

allOpen {
    annotation("app.aaps.annotations.OpenForTesting")
}

// One task, not one per variant: a multiplatform module has no product flavours. Same generator as
// :core:keys, :core:ui and :core:interfaces, pointed at this module's strings. It lets the classes here
// name their user text instead of numbering it, which is what a commonMain class needs. The strings
// themselves do not move, and AAPT keeps resolving them on Android exactly as before.
val generateImplementationStrings = tasks.register<GenerateKeyStringsTask>("generateImplementationStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.implementation")
    owner.set("implementation")
    objectName.set("ImplementationStrings")
    idsObjectName.set("ImplementationStringIds")
    reportFile.set(layout.buildDirectory.file("reports/implementationStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory only applies a convention derived from the task name,
    // so both properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/implementationStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/implementationStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.implementation"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        // Off by default for a multiplatform library, unlike a plain android library. This module
        // carries the maintenance and notification strings, so it must be on.
        androidResources { enable = true }
        // Creates the androidHostTest compilation, which also pulls in commonTest.
        // Restated from test-module-dependencies, which this module can no longer apply.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        // Restated from android-module-dependencies, which this module can no longer apply.
        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    // Apple klibs cross compile on Windows. Linking and running still need a Mac, and those tasks
    // report SKIPPED rather than failing. Keeping the targets is what stops an Android only import
    // from quietly reaching commonMain later.
    iosArm64()
    iosSimulatorArm64()

    // commonMain is small on purpose. Most of this module reaches Android directly, and most of the
    // rest formats user text through ResourceHelper and app.aaps.core.ui.R - the twenty command queue
    // classes are otherwise portable and fail only on that. They can follow once they take a
    // TextResolver and a TextRef instead, the same move :core:keys already made.
    sourceSets {
        commonMain {
            kotlin.srcDir(generateImplementationStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                // For UiStrings: the command queue names its user text instead of numbering it.
                implementation(project(":core:ui"))
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateImplementationStrings.flatMap { it.androidOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(libs.kotlinx.datetime)
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))
                implementation(project(":shared:impl"))
                implementation(libs.com.squareup.okhttp3.okhttp)

                // Everything here was `api` on the old android library, so it must stay exported.
                api(libs.androidx.datastore.preferences)
                api(project.dependencies.platform(libs.androidx.compose.bom))
                api(libs.androidx.compose.runtime)

                // Protection
                implementation(libs.androidx.biometric)
                // Logger
                implementation(libs.org.slf4j.api)
                implementation(libs.com.github.tony19.logback.android)
            }
        }

        // Hand written rather than taken from test-module-dependencies and
        // compose-test-module-dependencies, because both convention plugins apply
        // com.android.library and so cannot be used here.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":plugins:aps"))
                implementation(project(":pump:virtual"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.work.testing)

                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.joda.time)
                implementation(libs.com.google.truth)
                implementation(libs.org.skyscreamer.jsonassert)

                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                // Was debugImplementation: supplies the manifest holding the activity that
                // createComposeRule() launches. The multiplatform library target has no build types.
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Robolectric runs tests in its own classloader sandbox and rewrites bytecode, so the default
    // JaCoCo on-the-fly agent records no coverage for the classes those tests exercise. Restated
    // from jacoco-module-dependencies, which applies com.android.library.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// :shared:impl and :shared:tests are flavoured Android libraries, and a multiplatform module has no
// flavours of its own, so resolution would be ambiguous. Pin the same flavour the app builds with -
// neither module has flavour specific sources, so this only picks a variant, it does not change code.
// Same pin as :plugins:main and :plugins:aps.
listOf(
    "androidCompileClasspath",
    "androidRuntimeClasspath",
    "androidHostTestCompileClasspath",
    "androidHostTestRuntimeClasspath"
).forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(com.android.build.api.attributes.ProductFlavorAttr.of("standard"), objects.named("full"))
        }
    }
}
