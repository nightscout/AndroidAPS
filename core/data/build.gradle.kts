
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

    // No module-wide opt-in for ExperimentalNativeApi. `assert` is not in the common standard
    // library at all, so opting in could never have fixed it; the fix is the devAssert
    // expect/actual, and the Native actual scopes its own opt-in to one file.

    sourceSets {
        commonMain {
            dependencies {
                api(project.dependencies.platform(libs.kotlinx.serialization.bom))
                api(libs.kotlinx.serialization.json)
            }
        }
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
                // The oracle for OrgJsonCompatParityTest, and the only place org.json may appear.
                implementation(libs.org.json.android)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
