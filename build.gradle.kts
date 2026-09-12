import org.gradle.testing.jacoco.plugins.JacocoPlugin
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
    alias(libs.plugins.compose.compiler) apply false
    id(libs.plugins.android.test.get().pluginId) apply false
    // Aggregates the per-module coverage into one report.
    id("jacoco-aggregation")
}

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
            //freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
            // -Xannotation-default-target=param-property removed: it's the default since Kotlin 2.4, so the
            // flag is now redundant and the compiler warns about it on every module.
            jvmTarget.set(Versions.jvmTarget)
        }
    }
    // The build cache may replay compilation. It may not replay test results.
    //
    // A cached Test task does not run its tests - it restores the previous outcome and reports green.
    // By Gradle's rules that is sound, same inputs give the same result, and in the log it is
    // indistinguishable from a real run. This project has twice shipped tests that silently were not
    // executing (JUnit 5 skipping expression-body tests; the non-app instrumented step running nothing
    // for months), and both times the signal was a green build that proved nothing. For an app that
    // doses insulin that is not a trade worth making for a few seconds.
    //
    // This disables cache *reuse* only. Up-to-date checks still skip genuinely unchanged tests on a
    // local incremental build, and CI checks out fresh where nothing is up to date anyway. Compile
    // tasks keep the cache, and they are where nearly all of the saving is.
    tasks.withType<AbstractTestTask>().configureEach {
        outputs.doNotCacheIf("tests must actually run, not be replayed from a previous build") { true }
    }
    gradle.projectsEvaluated {
        tasks.withType<JavaCompile> {
            val compilerArgs = options.compilerArgs
            compilerArgs.add("-Xlint:deprecation")
            compilerArgs.add("-Xlint:unchecked")
        }
    }

    apply<JacocoPlugin>()
}

tasks.register<Delete>("clean") {
    description = "Cleanup generated code"
}.configure {
    delete(rootProject.layout.buildDirectory)
}
