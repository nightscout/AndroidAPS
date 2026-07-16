import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath(libs.com.android.tools.build)
        classpath(libs.com.google.gms)
        classpath(libs.com.google.firebase.gradle)

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files

        classpath(libs.kotlin.gradlePlugin)
        classpath(libs.kotlin.allopen)
        classpath(libs.kotlin.serialization)
    }
}

plugins {
    alias(libs.plugins.klint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler) apply false
    id(libs.plugins.android.test.get().pluginId) apply false
}

// Dagger/Hilt (≥2.57 unshades kotlin-metadata-jvm) ships a metadata reader that lags new Kotlin releases.
// Pin it to the Kotlin version so the Hilt KSP processor can parse current class metadata; captured here
// (where the `libs` catalog accessor is in scope) and forced per-configuration below.
val kotlinMetadataVersion = libs.versions.kotlin.get()

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
            freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
            // -Xannotation-default-target=param-property removed: it's the default since Kotlin 2.4, so the
            // flag is now redundant and the compiler warns about it on every module.
            jvmTarget.set(Versions.jvmTarget)
        }
    }
    gradle.projectsEvaluated {
        tasks.withType<JavaCompile> {
            val compilerArgs = options.compilerArgs
            compilerArgs.add("-Xlint:deprecation")
            compilerArgs.add("-Xlint:unchecked")
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")

    // Only affects configurations that actually pull kotlin-metadata-jvm (the Hilt/Dagger KSP processor
    // classpath), so it's a no-op elsewhere. Keeps the metadata reader in sync with future Kotlin bumps.
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinMetadataVersion")
        }
    }
}

// Setup all reports aggregation
apply(from = "jacoco_aggregation.gradle.kts")

tasks.register<Delete>("clean") {
    description = "Cleanup generated code"
}.configure {
    delete(rootProject.layout.buildDirectory)
}
