plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
}

kotlin {
    jvm {
        // Every consumer of :core:nssdk is still an Android module, and they resolve this variant.
        // Nothing about them changes.
        compilerOptions {
            jvmTarget.set(Versions.jvmTarget)
        }
    }

    // Stand-in for a real Kotlin/Native target, same as :core:data. iOS needs macOS and Xcode, but
    // mingwX64 compiles the same common code through Kotlin/Native, which is what proves there is no
    // JVM API left in commonMain. Replace or extend with iosArm64() / iosSimulatorArm64() on a Mac.
    mingwX64()

    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(libs.io.ktor.client.core)
                implementation(libs.io.ktor.client.content.negotiation)
                implementation(libs.io.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
            }
        }
        getByName("jvmMain") {
            dependencies {
                // OkHttp is both the Ktor engine and, on the JVM side, what the rest of the app
                // already uses. The client-control crypto in this source set is javax.crypto.
                api(libs.com.squareup.okhttp3.okhttp)
                implementation(libs.io.ktor.client.okhttp)
            }
        }
        getByName("mingwX64Main") {
            dependencies {
                // CIO is Ktor's own multiplatform engine, enough for the compile proof. An Apple
                // target would use ktor-client-darwin instead.
                implementation(libs.io.ktor.client.cio)
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                runtimeOnly(libs.org.junit.platform.launcher)
                implementation(libs.com.google.truth)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.kotlinx.coroutines.test)
                // A real HTTP server on localhost, so the contract tests exercise the whole stack.
                implementation(libs.com.squareup.okhttp3.mockwebserver)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
