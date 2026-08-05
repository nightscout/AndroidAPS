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

    // Stand-in for a real Kotlin/Native target. iOS targets need macOS and Xcode, which cannot run
    // on Windows, but mingwX64 compiles the same common code through Kotlin/Native, so it proves
    // there is no JVM API left in commonMain. Replace or extend with
    // iosArm64() / iosSimulatorArm64() on a Mac.
    mingwX64 {
        compilerOptions {
            // kotlin.assert has an experimental implementation on Native. ICfg.iobCalcForTreatment
            // uses it. Opting in here keeps that code exactly as it is - turning the asserts into
            // require() would change behaviour, because JVM assertions are off in production while
            // require() always throws.
            optIn.add("kotlin.experimental.ExperimentalNativeApi")
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
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
