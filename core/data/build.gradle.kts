import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        // Every consumer of :core:data is still an Android or JVM module, so this variant is the
        // one they resolve. Nothing about them changes.
        compilerOptions {
            jvmTarget.set(Versions.jvmTarget)
        }
    }

    // Real Apple targets. Kotlin/Native cross compiles klibs for them from any host, so these do
    // build on Windows - only linking, cinterop and running their tests need a Mac.
    iosArm64()
    iosSimulatorArm64()

    // mingwX64 stays, and not out of habit: it is the only Kotlin/Native target whose tests can
    // actually RUN on this machine. iosSimulatorArm64Test is disabled off macOS ("simulator tests
    // require macOS"), so without mingw there would be no way to execute common code through
    // Kotlin/Native at all before a Mac appears.
    mingwX64()

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                // kotlin.assert has an experimental implementation on Native. ICfg.iobCalcForTreatment
                // uses it. Opting in here keeps that code exactly as it is - turning the asserts into
                // require() would change behaviour, because JVM assertions are off in production while
                // require() always throws.
                compilerOptions.optIn.add("kotlin.experimental.ExperimentalNativeApi")
            }
        }
    }

    sourceSets {
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(libs.org.junit.jupiter)
                implementation(libs.com.google.truth)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
